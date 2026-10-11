#!/usr/bin/env bash
# Daily database backup for the Oracle server (Option A).
#
#  - dumps the flatmeals database into /var/backups/flatmeals/flatmeals-YYYY-MM-DD.sql.gz
#  - keeps the last 14 days on the server
#  - copies the folder to Google Drive with rclone (if you set it up; see docs/DEPLOY.md)
#
# Install:  sudo cp backup.sh /usr/local/bin/flatmeals-backup && sudo chmod 700 /usr/local/bin/flatmeals-backup
# Schedule: sudo crontab -e   and add the line
#           0 3 * * * /usr/local/bin/flatmeals-backup >> /var/log/flatmeals-backup.log 2>&1
set -euo pipefail

set -a; source /etc/flatmeals.env; set +a

DIR=/var/backups/flatmeals
FILE="$DIR/flatmeals-$(TZ=Asia/Dhaka date +%F).sql.gz"
mkdir -p "$DIR"
chmod 700 "$DIR"

# MYSQL_PWD keeps the password out of the process list
MYSQL_PWD="$DB_PASSWORD" mysqldump \
  --host="${DB_HOST:-localhost}" --port="${DB_PORT:-3306}" --user="$DB_USER" \
  --single-transaction --routines --triggers --no-tablespaces \
  "${DB_NAME:-flatmeals}" | gzip > "$FILE.part"
mv "$FILE.part" "$FILE"

find "$DIR" -name 'flatmeals-*.sql.gz' -mtime +14 -delete

if command -v rclone >/dev/null && rclone listremotes | grep -q '^gdrive:'; then
  rclone copy "$DIR" gdrive:flatmeals-backups --max-age 48h
fi

echo "$(date -Is) backup ok: $FILE ($(du -h "$FILE" | cut -f1))"
