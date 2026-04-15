# start.ps1
# -------------------------------------------------------------
# Startup script for UniOS Backend (Windows)
# Automatically checks for Java, Ollama, builds the JAR, and runs it
# -------------------------------------------------------------

$ErrorActionPreference = "Stop"

Write-Host "=============================" -ForegroundColor Cyan
Write-Host " Starting UniOS Ecosystem" -ForegroundColor Cyan
Write-Host "=============================" -ForegroundColor Cyan

# 1. Environment Check (Java 21+)
Write-Host "[1/5] Verifying Prerequisites..." -ForegroundColor Yellow

if (Get-Command java -ErrorAction SilentlyContinue) {
    # Silence the fake "NativeCommandError" just for this line
    $oldEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $javaOutput = java -version 2>&1 | Out-String
    $ErrorActionPreference = $oldEap

    if ($javaOutput -match "version") {
        $versionLine = $javaOutput -split "`n" | Select-String "version"
        Write-Host "-> Java Version: $versionLine" -ForegroundColor Green
    }
} else {
    Write-Host "CRITICAL ERROR: Java is not installed or not in PATH." -ForegroundColor Red
    Write-Host "Download Java 21+ from: https://adoptium.net/" -ForegroundColor Gray
    exit 1
}

# 2. Check Ollama Subsystem
Write-Host "[2/5] Checking LLM Subsystem..." -ForegroundColor Yellow

if (Get-Command ollama -ErrorAction SilentlyContinue) {
    $ollamaRunning = Get-Process -Name "ollama" -ErrorAction SilentlyContinue
    if (!$ollamaRunning) {
        Write-Host "-> Starting local Ollama server in background..."
        Start-Process "ollama" -ArgumentList "serve" -WindowStyle Hidden
        Start-Sleep -Seconds 3
    }

    Write-Host "-> Verifying llama3.2 is available..."
    # Silence fake "NativeCommandError" for ollama list
    $oldEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $modelCheck = ollama list 2>&1 | Out-String
    $ErrorActionPreference = $oldEap

    if (!($modelCheck -match "llama3\.2")) {
        Write-Host "-> Pulling llama3.2 (this may take a while)..."
        ollama pull llama3.2
    } else {
        Write-Host "-> llama3.2 is ready." -ForegroundColor Green
    }
} else {
    Write-Host "WARNING: Ollama is not installed or not in PATH." -ForegroundColor Yellow
    Write-Host "Download it from: https://ollama.com/" -ForegroundColor Gray
    Write-Host "Skipping LLM checks. The app may fail if Ollama is not manually started." -ForegroundColor Gray
}

# 3. Build Backend using Maven Wrapper
Write-Host "[3/5] Building Spring Boot Application..." -ForegroundColor Yellow
if (Test-Path ".\mvnw.cmd") {
    & .\mvnw package -DskipTests
} else {
    & .\mvnw package -DskipTests
}
if ($LASTEXITCODE -ne 0) {
    Write-Host "CRITICAL ERROR: Build failed. Cannot start server." -ForegroundColor Red
    exit 1
}

# 4. Find and Execute the FAT JAR
Write-Host "[4/5] Preparing runtime..." -ForegroundColor Yellow
$jarFile = Get-ChildItem -Path "target" -Filter "UniOS-1-*.jar" | Select-Object -First 1

if ($null -eq $jarFile) {
    Write-Host "CRITICAL ERROR: JAR file not found in target directory." -ForegroundColor Red
    exit 1
}

# 5. Database Reminder (if run for the first time)
if ($env:APP_DB_AUTO_SEED -eq "always") {
     Write-Host "-> NOTICE: Database AUTO-SEED is ACTIVE." -ForegroundColor Magenta
}

Write-Host "[5/5] Activating Server..." -ForegroundColor Yellow
Write-Host "Press CTRL+C at any time to shut down." -ForegroundColor DarkGray
Write-Host ""
# Run Java process
java -jar $jarFile.FullName
