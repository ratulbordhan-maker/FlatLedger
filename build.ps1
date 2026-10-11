# Builds ONE file that contains the whole app (website + API):
#     backend\target\flat-meal-manager.jar
#
# Run it in PowerShell from the project folder:
#     cd C:\flat-meal-manager
#     powershell -ExecutionPolicy Bypass -File .\build.ps1
#
# What it does:
#   1. builds the React website   (frontend\dist)
#   2. copies it into the backend (backend\src\main\resources\static)
#   3. runs all the tests and builds the jar
#
# Add  -SkipTests  to skip step 3's tests (only when you have just run them).

param([switch]$SkipTests)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

function Step($text) { Write-Host "`n==> $text" -ForegroundColor Green }
function Check($what) { if ($LASTEXITCODE -ne 0) { throw "$what failed (see the messages above)." } }

Step 'Building the website'
Push-Location "$root\frontend"
try {
    npm install;   Check 'npm install'
    npm run build; Check 'npm run build'
} finally { Pop-Location }

Step 'Copying the website into the backend'
$static = "$root\backend\src\main\resources\static"
if (Test-Path $static) { Remove-Item $static -Recurse -Force }
New-Item -ItemType Directory -Path $static | Out-Null
Copy-Item "$root\frontend\dist\*" $static -Recurse

Step 'Building the jar'
Push-Location "$root\backend"
try {
    if ($SkipTests) { mvn -q -DskipTests package } else { mvn -q package }
    Check 'mvn package'
} finally { Pop-Location }

Step 'Done'
Write-Host "Your app: $root\backend\target\flat-meal-manager.jar"
Write-Host 'Try it:   java -jar backend\target\flat-meal-manager.jar   then open http://localhost:8080'
