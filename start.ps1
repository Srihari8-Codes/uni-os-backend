# start.ps1
# -------------------------------------------------------------
# Startup script for UniOS Backend (Windows)
# Automatically checks for Ollama, builds the JAR, and runs it
# -------------------------------------------------------------

Write-Host "=============================" -ForegroundColor Cyan
Write-Host " Starting UniOS Ecosystem" -ForegroundColor Cyan
Write-Host "=============================" -ForegroundColor Cyan

# 1. Start Ollama Model Server Background Process
Write-Host "[1/4] Checking LLM Subsystem..." -ForegroundColor Yellow
$ollamaRunning = Get-Process -Name "ollama" -ErrorAction SilentlyContinue
if (!$ollamaRunning) {
    Write-Host "-> Starting local Ollama server in background..."
    Start-Process "ollama" -ArgumentList "serve" -WindowStyle Hidden
    Start-Sleep -Seconds 3
}

Write-Host "-> Verifying llama3.2 is available..."
# Run list command and check for model
$modelCheck = ollama list
if (!($modelCheck -match "llama3\.2")) {
    Write-Host "-> Pulling llama3.2 (this may take a while)..."
    ollama pull llama3.2
} else {
    Write-Host "-> llama3.2 is ready." -ForegroundColor Green
}

# 2. Go to backend directory
cd "Uni OS - 1"

# 3. Build Backend using Maven Wrapper
Write-Host "[2/4] Building Spring Boot Application..." -ForegroundColor Yellow
.\mvnw package -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "CRITICAL ERROR: Build failed. Cannot start server." -ForegroundColor Red
    exit 1
}

# 4. Find and Execute the FAT JAR
Write-Host "[3/4] Preparing runtime..." -ForegroundColor Yellow
$jarFile = Get-ChildItem -Path "target" -Filter "UniOS-1-*.jar" | Select-Object -First 1

if ($null -eq $jarFile) {
    Write-Host "CRITICAL ERROR: JAR file not found in target directory." -ForegroundColor Red
    exit 1
}

Write-Host "[4/4] Activating Server..." -ForegroundColor Yellow
Write-Host "Press CTRL+C at any time to shut down." -ForegroundColor DarkGray
Write-Host ""
# Run Java process
java -jar $jarFile.FullName
