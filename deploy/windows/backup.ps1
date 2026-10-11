# Daily database backup on your own Windows PC (Option C, or just for your laptop).
#
# Saves flatmeals-YYYY-MM-DD.sql.zip
# into a folder that OneDrive or Google Drive syncs, and keeps the last 14.
#
# Run by hand in PowerShell:
#   $env:FLATMEALS_DB_PASSWORD = 'your-db-password'
#   powershell -ExecutionPolicy Bypass -File C:\flat-meal-manager\deploy\windows\backup.ps1
# To run it every day, see docs/DEPLOY.md, Option C.

param(
    [string]$BackupFolder = "$env:USERPROFILE\OneDrive\flatmeals-backups",
    [string]$MySqlBin = 'C:\Program Files\MySQL\MySQL Server 8.4\bin',
    [string]$Database = 'flatmeals',
    [string]$User = 'flatmeals'
)
$ErrorActionPreference = 'Stop'

if (-not $env:FLATMEALS_DB_PASSWORD) { throw 'Set FLATMEALS_DB_PASSWORD first (see the top of this file).' }
New-Item -ItemType Directory -Force -Path $BackupFolder | Out-Null

$file = Join-Path $BackupFolder ("flatmeals-{0:yyyy-MM-dd}.sql" -f (Get-Date))
$env:MYSQL_PWD = $env:FLATMEALS_DB_PASSWORD
& "$MySqlBin\mysqldump.exe" --user=$User --single-transaction --routines --triggers --no-tablespaces `
    --result-file=$file $Database
if ($LASTEXITCODE -ne 0) { throw 'mysqldump failed.' }
Remove-Item Env:\MYSQL_PWD

Compress-Archive -Path $file -DestinationPath "$file.zip" -Force
Remove-Item $file
Get-ChildItem $BackupFolder -Filter 'flatmeals-*.sql.zip' |
    Sort-Object LastWriteTime -Descending | Select-Object -Skip 14 | Remove-Item

Write-Host "Backup saved: $file.zip"
