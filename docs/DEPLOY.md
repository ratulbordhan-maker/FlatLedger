# Putting Flat Meals online

This guide takes the app from your laptop to a real web address that everyone in the flat can open on their phone.

After Phase 8 the whole app is **one file**, `flat-meal-manager.jar`. It contains the website and the API, so the site and the data share one address. Wherever it runs, it needs two things:

1. **Java 21**, to run the jar.
2. **A MySQL database**, where every meal, expense and payment is stored.

You have three free options:

| | A. Oracle Cloud | B. Render + Aiven | C. Your own PC |
|---|---|---|---|
| Cost | Free (card needed to sign up) | Free (no card) | Free |
| Always on? | Yes | Sleeps after 15 minutes idle; the first visit then takes about a minute | Only while your PC is on |
| Where the data lives | MySQL on your server | Aiven's MySQL (1 GB) | MySQL on your PC |
| Effort | Most setup, least waiting | Easiest | Easy, but a stable web address needs a domain (~$10/year) |
| Backups | Script + Google Drive | GitHub runs them nightly | Script + OneDrive |

**My suggestion:** choose **A** if you're fine giving Oracle a card for verification. Otherwise choose **B**. Choose C only if one PC in the flat is always on.

Every option starts with Part 1.

---

## Part 1: Before you deploy (every option)

### 1.1 Build and try the one-file app on your laptop

In **PowerShell**:

```powershell
cd C:\flat-meal-manager
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

The build runs every test, so it takes a minute or two. When it finishes:

```powershell
java -jar backend\target\flat-meal-manager.jar
```

Open **http://localhost:8080**. You should see the login page. This time it is served by Spring Boot itself, not Vite (no `npm run dev` needed). Sign in and click around. Then refresh the browser on a page such as Meals: it should reload the same page, not show an error. Stop it with **Ctrl+C**.

> `-ExecutionPolicy Bypass` lets Windows run this one script without changing your settings.

### 1.2 Make your two secrets

You need two values that only you know.

- **JWT_SECRET**: the key that signs every login. Generate one in PowerShell:
  ```powershell
  [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }) -as [byte[]])
  ```
  Copy the long string it prints. Keep it in a password manager or a private note.
  If it ever leaks, change it and restart the app; everyone just signs in again.
- **MANAGER_PASSWORD**: the password for the first manager login, at least 8 characters.
  It is used only the very first time the app starts on an empty database. After that, change it in the app (avatar → Settings).

The live app **refuses to start** if either one is missing or still the example value. That is on purpose.

### 1.3 Put the code on GitHub (needed for B, handy for A)

1. On github.com, click **New repository**, name it `flat-meal-manager`, and choose **Private**.
2. In PowerShell:
   ```powershell
   cd C:\flat-meal-manager
   git remote add origin https://github.com/YOUR-NAME/flat-meal-manager.git
   git push -u origin master
   ```
The repository must stay **private**: for option B, the backups are stored with it.

Your test data does **not** go online. The live database starts empty and gets its own tables and first manager on the first start. Once it's live, follow `docs/FIRST-MONTH.md` to enter your real starting balances.

---

## Option A: Oracle Cloud Always Free

### A1. Create the server

1. Sign up at **cloud.oracle.com** (Free Tier). The card is only checked, not charged.
2. Do these two things straight away:
   - Go to **Billing → Budgets** and create a budget of $1 with an email alert. You'll hear about any charge immediately.
   - Note: Oracle may **reclaim idle Always Free servers** (a flat app is idle most of the time). Upgrading the account to **Pay As You Go** stops that, and Always Free resources stay free. Keep the budget alert on either way.
3. Create an SSH key on your laptop, in PowerShell (press Enter to accept the defaults):
   ```powershell
   ssh-keygen -t ed25519
   Get-Content $HOME\.ssh\id_ed25519.pub
   ```
4. Go to **Compute → Instances → Create instance**:
   - Image: **Canonical Ubuntu 24.04**.
   - Shape: **Ampere VM.Standard.A1.Flex**, with 1 OCPU and 6 GB memory. This is within Always Free.
   - SSH keys: **Paste public keys**, and paste the line printed in step 3.
   - Create the instance. Note its **Public IP address**.
5. Open the web ports. On the instance page, click the **subnet**, then the **Default Security List**, then **Add Ingress Rules**:
   - Source CIDR `0.0.0.0/0`, TCP, destination port `80`.
   - Same again for port `443`.

### A2. Get a free web address

1. Sign in at **duckdns.org** and create a subdomain, e.g. `ourflat` (which gives you `ourflat.duckdns.org`).
2. Set its IP to the server's public IP and click **update ip**.

### A3. Set up the server

Connect from PowerShell:

```powershell
ssh ubuntu@YOUR-SERVER-IP
```

The commands below run **on the server** (Linux). Paste them one block at a time.

```bash
# Updates, Java 21, MySQL, and the server's time zone
sudo apt update && sudo apt -y upgrade
sudo apt -y install openjdk-21-jre-headless mysql-server
sudo timedatectl set-timezone Asia/Dhaka

# Oracle's Ubuntu has its own firewall: allow web traffic in
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

Create the database. Pick a **new strong password** in place of `DB-PASSWORD`:

```bash
sudo mysql -e "CREATE DATABASE flatmeals CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'flatmeals'@'localhost' IDENTIFIED BY 'DB-PASSWORD';
GRANT ALL PRIVILEGES ON flatmeals.* TO 'flatmeals'@'localhost';"
```

MySQL only listens on the server itself, so nobody on the internet can reach it directly.

Install Caddy, which handles HTTPS:

```bash
sudo apt -y install debian-keyring debian-archive-keyring apt-transport-https curl
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update && sudo apt -y install caddy
```

Create a user for the app and a folder for it:

```bash
sudo useradd --system --home /opt/flatmeals --shell /usr/sbin/nologin flatmeals
sudo mkdir -p /opt/flatmeals && sudo chown flatmeals: /opt/flatmeals
```

### A4. Copy the app to the server

On your **laptop**, in a new PowerShell window:

```powershell
cd C:\flat-meal-manager
scp backend\target\flat-meal-manager.jar deploy\oracle\* ubuntu@YOUR-SERVER-IP:/tmp/
```

Back **on the server**:

```bash
sudo mv /tmp/flat-meal-manager.jar /opt/flatmeals/ && sudo chown flatmeals: /opt/flatmeals/flat-meal-manager.jar

# Settings and secrets
sudo cp /tmp/flatmeals.env.example /etc/flatmeals.env
sudo nano /etc/flatmeals.env      # fill in DB_PASSWORD, JWT_SECRET, MANAGER_*  (Ctrl+O Enter to save, Ctrl+X to quit)
sudo chmod 600 /etc/flatmeals.env # only root can read it

# Run the app as a service
sudo cp /tmp/flatmeals.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now flatmeals
sudo journalctl -u flatmeals -f   # watch it start; Ctrl+C to stop watching
```

Wait for `Started FlatMealsApplication`. Then switch on HTTPS:

```bash
sudo nano /tmp/Caddyfile          # change yourflat.duckdns.org to your address
sudo cp /tmp/Caddyfile /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

Open **https://ourflat.duckdns.org**. The padlock should appear within a minute, once Caddy has fetched the certificate.

### A5. Daily backups

```bash
sudo cp /tmp/backup.sh /usr/local/bin/flatmeals-backup && sudo chmod 700 /usr/local/bin/flatmeals-backup
sudo /usr/local/bin/flatmeals-backup          # try it once now
sudo crontab -e                                 # choose nano, then add this line at the bottom:
# 0 3 * * * /usr/local/bin/flatmeals-backup >> /var/log/flatmeals-backup.log 2>&1
```

This keeps 14 days of backups in `/var/backups/flatmeals` on the server. To also keep copies **off** the server, on Google Drive:

1. On your **laptop**, download rclone from rclone.org/downloads, unzip it, and in PowerShell inside that folder run:
   ```powershell
   .\rclone.exe authorize "drive"
   ```
   A browser opens. Allow access, then copy the long token it prints.
2. **On the server**, run `sudo apt -y install rclone` and then `sudo rclone config`:
   - choose **n** (new remote) and name it `gdrive`;
   - choose storage **drive**, and press Enter through the questions;
   - at "Use auto config?" answer **n**, then paste the token.

From then on, the nightly backup also copies to a `flatmeals-backups` folder in your Google Drive.

### A6. Updating later

On the laptop: run `build.ps1`, then `scp backend\target\flat-meal-manager.jar ubuntu@YOUR-SERVER-IP:/tmp/`. On the server:

```bash
sudo mv /tmp/flat-meal-manager.jar /opt/flatmeals/ && sudo chown flatmeals: /opt/flatmeals/flat-meal-manager.jar
sudo systemctl restart flatmeals
```

Database changes (new `V2__...sql` files) are applied automatically by Flyway when the app starts.

---

## Option B: Render + Aiven (no card)

### B1. The database on Aiven

1. Sign up at **console.aiven.io**.
2. Click **Create service**, choose **MySQL** and the **Free plan**, and pick a region close to Bangladesh (e.g. Singapore or Mumbai if offered).
3. When it says **Running**, open it and note the **Host**, **Port**, **User** (`avnadmin`), **Password** and **Database name** (`defaultdb`).
4. Your database address (`DB_URL`) is built from those values:
   ```
   jdbc:mysql://HOST:PORT/defaultdb?sslMode=REQUIRED&serverTimezone=Asia/Dhaka
   ```
   `sslMode=REQUIRED` encrypts the connection. Aiven insists on it.

Aiven may **power off a free database that hasn't been used for a while** and will email you first. If the site suddenly can't sign anyone in, check the Aiven console and press **Power on**.

### B2. The app on Render

1. Sign up at **render.com** with your GitHub account.
2. Click **New → Blueprint**, choose your `flat-meal-manager` repository, and Render reads `render.yaml`.
3. It asks for the secret values:

   | Name | Value |
   |---|---|
   | `DB_URL` | the address from B1 step 4 |
   | `DB_USER` | `avnadmin` |
   | `DB_PASSWORD` | Aiven's password |
   | `MANAGER_NAME` | your real name (you become the first member) |
   | `MANAGER_USERNAME` | e.g. `manager` |
   | `MANAGER_PASSWORD` | your first password (8+ characters) |

   `JWT_SECRET` is generated for you automatically.
4. Click **Apply**. The first build takes 5–10 minutes. Follow it in **Logs** until you see `Started FlatMealsApplication`.
5. Your app is at **https://flat-meals.onrender.com** (or a similar name shown at the top), with HTTPS already on.

About sleeping: after 15 minutes with no visitors, Render pauses the app. The next visit wakes it, which takes about a minute, and then it's quick again. Your data is safe; it lives in Aiven, not on Render.

Every `git push` to GitHub redeploys the app automatically.

### B3. Nightly backups with GitHub

The file `.github/workflows/backup.yml` already does this. You only give it the database details:

1. On GitHub, open your repository, then **Settings → Secrets and variables → Actions → New repository secret**.
2. Add `DB_HOST`, `DB_PORT`, `DB_USER` and `DB_PASSWORD`, using the values from Aiven.
3. Go to **Actions → Database backup → Run workflow** to make one backup now and check it works.

Every night at 03:00 Dhaka time a new backup appears under **Actions → Database backup → (a run) → Artifacts**, and each one is kept for 30 days. Aiven also keeps its own short-term backups.

> **Is my repository private?** Its name on GitHub has a **Private** label next to it. If it says Public, go to **Settings → General → Danger zone → Change visibility**.

---

## Option C: Your own Windows PC + Cloudflare Tunnel

Use this only if one PC in the flat stays on day and night.

1. Run `build.ps1`, then copy `backend\target\flat-meal-manager.jar` to `C:\flatmeals\`.
2. Create `C:\flatmeals\start.ps1` with your values:
   ```powershell
   $env:SPRING_PROFILES_ACTIVE = 'prod'
   $env:DB_USER = 'flatmeals'; $env:DB_PASSWORD = 'your-db-password'
   $env:JWT_SECRET = 'your-long-secret'
   $env:MANAGER_NAME = 'Your Name'; $env:MANAGER_USERNAME = 'manager'; $env:MANAGER_PASSWORD = 'your-first-password'
   java -jar C:\flatmeals\flat-meal-manager.jar
   ```
3. Use a separate database for the live data, not your test database: run `docs/reset-local-db.sql` once, or create a new database and set `DB_NAME`.
4. Make it start when you sign in:
   - open **Task Scheduler → Create Task**;
   - Trigger: **At log on**;
   - Action: program `powershell.exe`, arguments `-ExecutionPolicy Bypass -WindowStyle Hidden -File C:\flatmeals\start.ps1`.
5. Install **cloudflared** from Cloudflare's website.
   - **Quick test:** `cloudflared tunnel --url http://localhost:8080` prints a temporary `https://….trycloudflare.com` address. It changes every time it restarts.
   - **Fixed address:** you need a domain added to a free Cloudflare account. Then follow Cloudflare's "Create a tunnel" guide (Zero Trust → Networks → Tunnels) and point it at `http://localhost:8080`.
6. Backups: in Task Scheduler, run `deploy\windows\backup.ps1` daily. It saves into your OneDrive folder.
   - Set the password once with `[Environment]::SetEnvironmentVariable('FLATMEALS_DB_PASSWORD','your-db-password','User')`.

Set **Power & sleep** to *Never* while plugged in, or the site goes down whenever the PC sleeps.

---

## Every setting in one place

| Variable | Needed? | Example | What it's for |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | **Yes** (live) | `prod` | Turns on the safe live settings in `application-prod.yml` |
| `JWT_SECRET` | **Yes** (live) | 64 random characters | Signs logins. No default when live. |
| `MANAGER_PASSWORD` | **Yes** (live) | `a-long-password` | First manager's password, used once on an empty database |
| `MANAGER_NAME` | No | `Rakib` | First manager's member name (default `Manager`) |
| `MANAGER_USERNAME` | No | `rakib` | First manager's login name (default `manager`) |
| `DB_URL` | Either this… | `jdbc:mysql://host:port/defaultdb?sslMode=REQUIRED&serverTimezone=Asia/Dhaka` | Full database address. Needed for Aiven. |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | …or these | `localhost`, `3306`, `flatmeals` | Database address in parts |
| `DB_USER`, `DB_PASSWORD` | **Yes** (live) | `flatmeals`, … | Database login |
| `DB_POOL_SIZE` | No | `5` | Database connections the app keeps open |
| `JWT_EXPIRY_HOURS` | No | `12` | How long a login lasts |
| `PORT` | Set by Render | `10000` | Which port the app listens on (default 8080) |

---

## Restoring a backup

A backup is a `.sql` file (zipped) that rebuilds every table with its data. It replaces whatever is in the database you restore it into.

**Test a restore once, on your laptop, so you know it works:**

1. Unzip the backup. A `.sql.gz` file opens with 7-Zip (free).
2. In MySQL Workbench, run `CREATE DATABASE flatmeals_restore_test;`.
3. Go to **Server → Data Import → Import from Self-Contained File**, choose the `.sql` file, set **Default Target Schema** to `flatmeals_restore_test`, and click **Start Import**.
4. Open the tables and check that your members and expenses are there.

**For real, after a disaster:**

- **A (Oracle), on the server:**
  ```bash
  sudo systemctl stop flatmeals
  gunzip -c /var/backups/flatmeals/flatmeals-2026-10-11.sql.gz | sudo mysql flatmeals
  sudo systemctl start flatmeals
  ```
- **B (Aiven):** unzip the artifact, then import it with Workbench.
  - Connect to Aiven with Host, Port and `avnadmin`, and set **SSL → Use SSL: Require**.
  - Use **Data Import** into `defaultdb`.
  - Restart the service on Render afterwards (**Manual Deploy → Restart**).
- **C:** stop the app, then use Workbench **Data Import** into your live database.

---

## Check that it works (do this after every deploy)

1. **https://YOUR-ADDRESS/api/health** shows `{"status":"OK"}`.
2. The site shows the login page with a padlock (HTTPS).
3. Sign in as the manager, then **immediately change the password** in Settings.
4. Open the Meals page and press refresh: the same page comes back.
5. Create a member login, sign in as that member on your phone using **mobile data** (not Wi-Fi), and check:
   - they see their own account;
   - opening `/m/2026/10/report` tells them it's manager only;
   - they cannot open Payments or Cash.
6. As the manager, open the dashboard and go to **Month report → Download CSV**. The file opens in Excel or Google Sheets.
7. Type a wrong password 6 times for a test login: the 6th attempt says "Too many wrong passwords".
