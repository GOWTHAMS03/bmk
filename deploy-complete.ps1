# Complete AWS Deployment Guide for BusyMumKitchen
# This script orchestrates the full deployment process

param(
    [switch]$SkipInfrastructure = $false,
    [switch]$BuildDocker = $false,
    [switch]$DeployToEC2 = $false
)

function Write-Success { param($msg) Write-Host "[SUCCESS] $msg" -ForegroundColor Green }
function Write-Error-Custom { param($msg) Write-Host "[ERROR] $msg" -ForegroundColor Red }
function Write-Warning-Custom { param($msg) Write-Host "[WARNING] $msg" -ForegroundColor Yellow }
function Write-Info { param($msg) Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Write-Header { param($msg) Write-Host "`n==================== $msg ====================`n" -ForegroundColor Magenta }

Write-Header "🚀 BusyMumKitchen - Complete AWS Deployment Guide"

# Check prerequisites
Write-Info "Checking prerequisites..."

$checks = @(
    @{ Name = "AWS CLI"; Command = { aws --version 2>&1 } },
    @{ Name = "Docker"; Command = { docker --version 2>&1 } },
    @{ Name = "PowerShell 5.1+"; Command = { $PSVersionTable.PSVersion } }
)

$allChecked = $true
foreach ($check in $checks) {
    try {
        $result = & $check.Command
        Write-Success "$($check.Name): OK"
    } catch {
        Write-Error-Custom "$($check.Name): FAILED"
        $allChecked = $false
    }
}

if (-not $allChecked) {
    Write-Error-Custom "Some prerequisites are missing. Please install required tools."
    exit 1
}

# Menu
Write-Host @"
╔═══════════════════════════════════════════════════════════════╗
║           SELECT DEPLOYMENT PHASE                             ║
╚═══════════════════════════════════════════════════════════════╝

1️⃣  FULL DEPLOYMENT (Infrastructure + Build + Deploy)
2️⃣  INFRASTRUCTURE ONLY (AWS setup, RDS, EC2)
3️⃣  BUILD & PUSH Docker (Build image, push to ECR)
4️⃣  DEPLOY TO EC2 (Copy files and start services)
5️⃣  VIEW DEPLOYMENT INFO (See saved configuration)
6️⃣  EXIT

"@

$choice = Read-Host "Enter your choice (1-6)"

switch ($choice) {
    "1" {
        Write-Success "Starting FULL DEPLOYMENT..."
        & "$PSScriptRoot\deploy-aws.ps1"
        $infrastructure = $true
        $docker = $true
        $deploy = $true
    }
    "2" {
        Write-Success "Starting INFRASTRUCTURE SETUP..."
        & "$PSScriptRoot\deploy-aws.ps1"
        $infrastructure = $true
        $docker = $false
        $deploy = $false
    }
    "3" {
        Write-Success "Starting DOCKER BUILD & PUSH..."
        & "$PSScriptRoot\build-docker.ps1" -Push
        $infrastructure = $false
        $docker = $true
        $deploy = $false
    }
    "4" {
        Write-Success "Starting DEPLOYMENT TO EC2..."
        Write-Warning-Custom "This assumes infrastructure is already set up"
        $infrastructure = $false
        $docker = $false
        $deploy = $true
    }
    "5" {
        if (Test-Path "$PSScriptRoot\DEPLOYMENT_SUMMARY.txt") {
            Get-Content "$PSScriptRoot\DEPLOYMENT_SUMMARY.txt"
        } else {
            Write-Error-Custom "No deployment summary found. Run infrastructure setup first."
        }
        exit 0
    }
    "6" {
        Write-Info "Exiting..."
        exit 0
    }
    default {
        Write-Error-Custom "Invalid choice"
        exit 1
    }
}

# After infrastructure setup
if ($infrastructure) {
    Write-Header "▶ Phase 1: Infrastructure Setup Complete ✅"

    if ((Read-Host "Continue to Docker build? (y/n)") -eq "y") {
        $docker = $true
    }
}

# Docker build
if ($docker) {
    Write-Header "▶ Phase 2: Building Docker Image"

    Write-Info "This will build and optionally push the Docker image to ECR"

    $region = Read-Host "Enter AWS Region (default: ap-south-1)"
    if ([string]::IsNullOrEmpty($region)) { $region = "ap-south-1" }

    $pushToECR = (Read-Host "Push to ECR? (y/n)") -eq "y"

    if ($pushToECR) {
        & "$PSScriptRoot\build-docker.ps1" -Region $region -Push
    } else {
        & "$PSScriptRoot\build-docker.ps1" -Region $region
        Write-Warning-Custom "Image built but not pushed. Run with -Push flag to push to ECR"
    }

    if ((Read-Host "Continue to EC2 deployment? (y/n)") -eq "y") {
        $deploy = $true
    }
}

# Deploy to EC2
if ($deploy) {
    Write-Header "▶ Phase 3: Deploy to EC2"

    # Read deployment info
    if (-not (Test-Path "$PSScriptRoot\DEPLOYMENT_SUMMARY.txt")) {
        Write-Error-Custom "Deployment summary not found. Run infrastructure setup first."
        exit 1
    }

    $summaryContent = Get-Content "$PSScriptRoot\DEPLOYMENT_SUMMARY.txt" -Raw

    # Extract EC2 info using regex
    if ($summaryContent -match "IP Address:\s+(\d+\.\d+\.\d+\.\d+)") {
        $EC2_IP = $matches[1]
    }
    if ($summaryContent -match "Key Pair:\s+(\S+)") {
        $EC2_KEY = $matches[1]
    }

    if ([string]::IsNullOrEmpty($EC2_IP)) {
        $EC2_IP = Read-Host "Enter EC2 IP Address"
    }
    if ([string]::IsNullOrEmpty($EC2_KEY)) {
        $EC2_KEY = Read-Host "Enter EC2 Key Pair name"
    }

    # Prefer a BOM-free fixed key file if it exists (bmk-key-fixed.pem). Fall back to original .pem
    $candidateFixed = "$PSScriptRoot\$EC2_KEY-fixed.pem"
    $candidateOriginal = "$PSScriptRoot\$EC2_KEY.pem"
    if (Test-Path $candidateFixed) {
        $keyPath = $candidateFixed
        Write-Info "Using fixed key file: $keyPath"
    } elseif (Test-Path $candidateOriginal) {
        $keyPath = $candidateOriginal
        Write-Info "Using original key file: $keyPath"
    } else {
        $keyPath = $candidateOriginal
        Write-Warning-Custom "Key file not found locally; expected $candidateFixed or $candidateOriginal. Will attempt to use: $keyPath"
    }
    $envPath = "$PSScriptRoot\.env.aws"
    $composeFile = "$PSScriptRoot\deploy\docker-compose.aws.yml"

    Write-Info "EC2 IP: $EC2_IP"
    Write-Info "Key Path: $keyPath"

    # Verify files exist
    if (-not (Test-Path $keyPath)) {
        Write-Error-Custom "SSH key not found: $keyPath"
        exit 1
    }

    if (-not (Test-Path $envPath)) {
        Write-Error-Custom "Environment file not found: $envPath"
        Write-Warning-Custom "Create .env.aws with your configuration"
        exit 1
    }

    Write-Info "Copying files to EC2..."

    $sshBase = "ssh -i `"$keyPath`" -o StrictHostKeyChecking=no -o ConnectTimeout=30"
    $scpBase = "scp -i `"$keyPath`" -o StrictHostKeyChecking=no -o ConnectTimeout=30"
    $EC2_REGISTRY = ($ECR_URI -replace "/bmk-backend","" -replace "https://","")
    # Fall back: derive registry from summary if ECR_URI not populated here
    if ([string]::IsNullOrEmpty($EC2_REGISTRY)) {
        $EC2_REGISTRY = "776805327963.dkr.ecr.ap-south-1.amazonaws.com"
    }

    # 1. Ensure remote directory exists
    Write-Info "Ensuring /opt/bmk/deploy exists on EC2..."
    # Try SSH up to 6 times with exponential backoff — instances may require extra time to finish cloud-init
    $maxAttempts = 6
    $attempt = 0
    $mkdirResult = $null
    while ($attempt -lt $maxAttempts) {
        $attempt++
        Write-Info "Attempt ${attempt}/${maxAttempts}: checking SSH connectivity to ubuntu@${EC2_IP}..."
        $mkdirResult = Invoke-Expression "$sshBase ubuntu@${EC2_IP} 'sudo mkdir -p /opt/bmk/deploy && sudo chown ubuntu:ubuntu /opt/bmk/deploy && chmod 700 /opt/bmk/deploy'" 2>&1
        $code = $LASTEXITCODE
        if ($code -eq 0) {
            Write-Success "Remote directory ready"
            break
        }
        Write-Warning-Custom "SSH attempt $attempt failed (exit $code). Output: $mkdirResult"
        if ($attempt -lt $maxAttempts) {
            $wait = 5 * $attempt
            Write-Info "Waiting $wait seconds before retrying..."
            Start-Sleep -Seconds $wait
        }
    }
    if ($LASTEXITCODE -ne 0 -and $attempt -ge $maxAttempts) {
        Write-Error-Custom "All SSH attempts failed. Last output:`n$mkdirResult"
        Write-Info "Quick checks:"
        Write-Info "  - Is the EC2 public IP correct? ($EC2_IP)"
        Write-Info "  - Does the security group ($SG_ID) allow port 22 from your IP?"
        Write-Info "  - Is your private key file valid and readable: $keyPath"
        Write-Info "  - Wait a minute and try again if the instance is still starting"
        exit 1
    }

    # 2. Upload docker-compose.yml
    Write-Info "Uploading docker-compose.yml..."
    $scpOut = Invoke-Expression "$scpBase `"$composeFile`" ubuntu@${EC2_IP}:/opt/bmk/deploy/docker-compose.yml" 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Error-Custom "SCP docker-compose.yml failed: $scpOut"
        exit 1
    }
    Write-Success "docker-compose.yml uploaded"

    # 3. Upload .env
    Write-Info "Uploading .env file..."
    $scpOut2 = Invoke-Expression "$scpBase `"$envPath`" ubuntu@${EC2_IP}:/opt/bmk/deploy/.env" 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Error-Custom "SCP .env failed: $scpOut2"
        exit 1
    }
    Invoke-Expression "$sshBase ubuntu@${EC2_IP} 'chmod 600 /opt/bmk/deploy/.env'" 2>&1 | Out-Null
    Write-Success ".env uploaded and secured"

    # 4. Login to ECR on EC2, pull image and start services
    Write-Info "Logging EC2 into ECR, pulling image and starting Docker Compose..."
    $EC2_ECR_REGION = "ap-south-1"
    # Create a start script on the EC2 host that captures stdout/stderr to /tmp/bmk-start.log
    # This version uses the official convenience install and a direct Compose CLI binary
    # to avoid reliance on lsb_release/dpkg/gpg during scripted runs.
    $startTemplate = @'
#!/bin/bash
set -e
exec > /tmp/bmk-start.log 2>&1
echo "[START] bmk-start.sh running on: $(hostname)"

# Install Docker Engine using the official convenience script if missing
if ! command -v docker >/dev/null 2>&1; then
  echo "[INFO] docker not found — installing via get.docker.com"
  curl -fsSL https://get.docker.com | sh
  echo "[INFO] docker installed by get.docker.com"
else
  echo "[INFO] docker already installed"
fi

# Ensure docker service is running
if command -v systemctl >/dev/null 2>&1; then
  systemctl enable --now docker || true
fi

# Install Docker Compose CLI plugin (binary) if missing — fallback to official release
if ! docker compose version >/dev/null 2>&1; then
  echo "[INFO] docker compose CLI not found — installing CLI plugin"
  mkdir -p /usr/local/lib/docker/cli-plugins
  COMPOSE_VER="v2.21.2"
  curl -fsSL "https://github.com/docker/compose/releases/download/${COMPOSE_VER}/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/lib/docker/cli-plugins/docker-compose
  chmod +x /usr/local/lib/docker/cli-plugins/docker-compose || true
  echo "[INFO] docker compose CLI installed"
else
  echo "[INFO] docker compose CLI present"
fi

echo "[INFO] Logging into ECR and starting compose"
aws ecr get-login-password --region __ECR_REGION__ | sudo docker login --username AWS --password-stdin __ECR_REGISTRY__
cd /opt/bmk/deploy
# Use sudo for docker commands to be safe in automation contexts
sudo docker compose pull || true
sudo docker compose up -d --remove-orphans
sudo docker compose ps
echo "[END] bmk-start.sh completed"
'@

    # Replace placeholders with values; compute dpkg arch and lsb codename on the remote host, so leave shell substitutions literal
    $dpkgArch = '$(dpkg --print-architecture)'
    $lsbCode = '$(lsb_release -cs)'
    $startCmd = $startTemplate -replace '__DPKG_ARCH__', $dpkgArch -replace '__LSB_CODENAME__', $lsbCode -replace '__ECR_REGION__', $EC2_ECR_REGION -replace '__ECR_REGISTRY__', $EC2_REGISTRY
    # Ensure LF-only line endings (not CRLF) — CRLF causes bash on Linux to fail with \r: command not found
    $startCmd = $startCmd -replace "`r`n", "`n" -replace "`r", "`n"
    $tmpSh = [System.IO.Path]::GetTempFileName() + ".sh"
    # Write without BOM and without CRLF
    [System.IO.File]::WriteAllText($tmpSh, $startCmd, (New-Object System.Text.UTF8Encoding($false)))

    $scpShOut = Invoke-Expression "$scpBase `"$tmpSh`" ubuntu@${EC2_IP}:/tmp/bmk-start.sh" 2>&1
    Remove-Item $tmpSh -ErrorAction SilentlyContinue
    if ($LASTEXITCODE -ne 0) {
        Write-Error-Custom "Failed to upload start script to EC2: $scpShOut"
        exit 1
    }
    Write-Info "Start script uploaded to EC2"

    # Run the start script on the remote host and capture exit code and logs robustly.
    # Use bash -x to include traces in the log and write both stdout/stderr to /tmp/bmk-start.log.
    # The remote command will print a single marker line 'BMK_EXIT:<code>' before the log contents
    # so we can detect the script exit code reliably even if the SSH command succeeds.
    $remoteCmd = 'sudo bash -lc "bash -x /tmp/bmk-start.sh > /tmp/bmk-start.log 2>&1; echo BMK_EXIT:$?; cat /tmp/bmk-start.log"'
    $startOut = Invoke-Expression "$sshBase ubuntu@${EC2_IP} $remoteCmd" 2>&1
    Write-Host $startOut
    # Try to extract BMK_EXIT value from the combined output
    $exitCode = 1
    if ($startOut -match 'BMK_EXIT:(\d+)') { $exitCode = [int]$matches[1] }

    if ($exitCode -eq 0) {
        Write-Success "Docker services started successfully!"

        Write-Info "Waiting for app to become healthy (45 seconds)..."
        Start-Sleep -Seconds 45

        Write-Info "Checking application health..."
        $health = Invoke-Expression "$sshBase ubuntu@${EC2_IP} 'docker compose -f /opt/bmk/deploy/docker-compose.yml logs --tail=20 backend'" 2>&1
        Write-Host $health

        Write-Success "Deployment completed!"
        Write-Info "Access your application at: http://${EC2_IP}:8080"
        Write-Info "API Docs: http://${EC2_IP}:8080/swagger-ui.html"
        Write-Info "Health: http://${EC2_IP}:8080/api/v1/actuator/health"
    } else {
        Write-Error-Custom "Failed to start Docker Compose"
        Write-Info "Fetching remote start log (/tmp/bmk-start.log) for diagnosis..."
        $remoteLog = Invoke-Expression "$sshBase ubuntu@${EC2_IP} 'sudo cat /tmp/bmk-start.log'" 2>&1
        Write-Host "---- /tmp/bmk-start.log ----"
        Write-Host $remoteLog
        Write-Host "---- end remote log ----"
        Write-Info "SSH into EC2 to investigate further:"
        Write-Info "  ssh -i $keyPath ubuntu@$EC2_IP"
        Write-Info "  cd /opt/bmk/deploy"
        Write-Info "  docker compose logs -f"
        exit 1
    }
}

Write-Header "✨ Deployment Complete!"

Write-Host @"
📋 USEFUL COMMANDS:

View logs:
  ssh -i "$keyPath" ubuntu@${EC2_IP} "cd /opt/bmk/deploy && docker compose logs -f backend"

Restart services:
  ssh -i "$keyPath" ubuntu@${EC2_IP} "cd /opt/bmk/deploy && docker compose restart"

Health check:
  curl http://$EC2_IP:8080/api/v1/actuator/health

Updated .env on EC2:

  ssh -i "$keyPath" ubuntu@${EC2_IP} "sudo mv /tmp/.env /opt/bmk/deploy/.env && sudo chmod 600 /opt/bmk/deploy/.env && cd /opt/bmk/deploy && docker compose restart"

"@

