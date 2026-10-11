# Your first real month

Do this once, right after the live site works (see `DEPLOY.md`). It moves the flat from the old spreadsheet into the app without losing anyone's balance.

Have the old spreadsheet open next to you. For each person you need two numbers:

- **their balance**: what they owe the flat, or what the flat owes them;
- **the flat cash they are holding**: money collected for the flat that is still in their hands.

## 1. Sign in and secure your login

1. Open the live site and sign in with `MANAGER_USERNAME` and `MANAGER_PASSWORD`.
2. Click your avatar (top right), then **Settings**, then **Change password**.

   From now on the password in your server settings is no longer used to sign in. Keep it set anyway: the live app refuses to start without it.

## 2. Open the first month

The home page says **No months yet**. Click **Open October 2026** (or whichever month it shows).

- Pick the month you are starting with, normally the current one.
- Months must follow each other with no gaps, so you can't go back and add earlier months later. That's fine: the old months stay in the spreadsheet, and their result comes in as starting balances in step 4.

## 3. Add everyone

Go to **Members**:

1. Your own member is called whatever `MANAGER_NAME` was. If it needs fixing, use **Rename**.
2. Click **Add member** for each flatmate.
3. Click **Add to October 2026** for everyone who eats in the flat this month.
4. Click **Create sign-in** for each person, with the role **View only**. Give them their username and password in person, and ask them to change the password in Settings.

## 4. Copy over starting balances

Go to **Payments**. Under **Balance adjustments**, click **Add adjustment** once per person who doesn't start at zero:

| In the spreadsheet | Kind | Amount |
|---|---|---|
| Rafi owes ৳300 | Starting balance | **They owe more**, 300 |
| The flat owes Srijila ৳200 | Starting balance | **Give them credit**, 200 |
| Exactly settled | (skip) | |

For the reason, write something like `From September spreadsheet`.

## 5. Record the cash people hold

Go to **Cash** and click **Correct cash** once per person holding flat money:

- Kind: **Cash they already had when we started**
- Direction: **Add cash**
- Amount: what they hold right now. If you can, count it first.

## 6. Check before you go further

Open **Overview** and compare it with the spreadsheet:

- Each person's **Brought forward** is 0. Their starting balance shows under **Adjustments** in their account, so their **Balance** should equal the spreadsheet figure.
- **Flat cash** equals the total cash people hold.

If a number is wrong, **Cancel** that adjustment (give a reason) and add the right one. Nothing is ever deleted, so the mistake stays visible in the history, crossed out.

## 7. Carry on as normal

From now on:

- **Daily:** fill in **Meals**, and add **Expenses** after each market trip.
- **When someone pays:** record it under **Payments**.
- **Now and then:** do a **Record a cash count** on the Cash page.
- **At the end of the month:**
  1. Open **Overview → Month report**. Print it or **Download CSV** to share in the flat group.
  2. Then click **Close October 2026**. This freezes the meal rate and everyone's balances, which carry into the next month automatically.
  3. Open the next month from the month switcher at the top.

## Throwing away test data on your laptop

Your laptop's database still has your test data, which is fine for practising. To start your laptop afresh too, follow the steps at the top of `docs/reset-local-db.sql`. **Never** run it on the live server.
