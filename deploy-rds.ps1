#Requires -Version 5.1
<#
.SYNOPSIS
    Full end-to-end deployment: build Docker image → push to ECR → provision AWS
    infra (RDS, EC2, Security Groups, ECR) → deploy app on EC2 wired to RDS.

.DESCRIPTION
    Idempotent — re-running is safe; existing resources are reused.
    Prerequisites: AWS CLI v2, Docker Desktop (running), SSH / SCP in PATH.

.PARAMETER Region
    AWS region.  Default: ap-south-1
.PARAMETER RepositoryName
    ECR repository name.  Default: bmk-backend
.PARAMETER DBInstanceId
    RDS instance identifier.  Default: bmk-postgres
.PARAMETER DBName
    PostgreSQL database name.  Default: busymumkitchen
.PARAMETER DBUsername
    RDS master username.  Default: bmk_user
.PARAMETER DBPassword
    RDS master password.  Leave empty to auto-generate.
.PARAMETER InstanceType
    EC2 instance type.  Default: t3.medium
.PARAMETER InstanceName
    Name tag for the EC2 instance.  Default: bmk-backend
.PARAMETER SkipBuild
    Skip the Docker build step (use an image already in ECR).
.PARAMETER SkipDeploy
    Stop after pushing to ECR; do not touch EC2.
.PARAMETER JwtSecret
    JWT signing secret.  Leave empty to auto-generate.

.EXAMPLE
    # Full deploy with auto-generated passwords
    .\deploy-rds.ps1

.EXAMPLE
    # Provide your own DB password and skip build
    .\deploy-rds.ps1 -DBPassword "MySecureP@ss!" -SkipBuild
#>

param(
    [string]$Region         = "ap-south-1",
    [string]$RepositoryName = "bmk-backend",
    [string]$DBInstanceId   = "bmk-postgres",
    [string]$DBName         = "busymumkitchen",
    [string]$DBUsername     = "bmk_user",
    [string]$DBPassword     = "",
    [string]$InstanceType   = "t3.medium",
    [string]$InstanceName   = "bmk-backend",
    [string]$JwtSecret      = "",
    [switch]$SkipBuild,
    [switch]$SkipDeploy
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ─── Helpers ───────────────────────────────────────────────────────────────────
function Write-Step   { param($n,$t) Write-Host "`n▶  STEP $n : $t" -ForegroundColor Magenta; Write-Host ("─" * 60) -ForegroundColor DarkGray }
function Write-OK     { Write-Host "  ✔  $($args[0])" -ForegroundColor Green }
function Write-Warn   { Write-Host "  ⚠  $($args[0])" -ForegroundColor Yellow }
function Write-Err    { Write-Host "  ✘  $($args[0])" -ForegroundColor Red }
function Write-Info   { Write-Host "     $($args[0])" -ForegroundColor Cyan }
function Fail         { Write-Err $args[0]; exit 1 }

function Assert-ExitCode {
    param([string]$Msg)
    if ($LASTEXITCODE -ne 0) { Fail $Msg }
}

function New-RandomPassword {
    param([int]$Length = 24)
    # Only alphanumeric + a few safe specials (no single quotes, @, /, \)
    $chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!#%^&*()-_=+'
    -join ((1..$Length) | ForEach-Object { $chars[(Get-Random -Maximum $chars.Length)] })
}

function New-RandomSecret {
    param([int]$Bytes = 64)
    $b = New-Object byte[] $Bytes
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
    [Convert]::ToBase64String($b)
}

# ─── Banner ────────────────────────────────────────────────────────────────────
Write-Host @"

╔══════════════════════════════════════════════════════════════════════════════╗
║          BusyMumKitchen  —  Full AWS Deployment  (ECR + RDS + EC2)          ║
╚══════════════════════════════════════════════════════════════════════════════╝
  Region : $Region
  ECR    : $RepositoryName
  RDS    : $DBInstanceId  ($DBName)
  EC2    : $InstanceName  ($InstanceType)
"@ -ForegroundColor Cyan

# ═══════════════════════════════════════════════════════════════════════════════
# STEP 0 — Prerequisites
# ═══════════════════════════════════════════════════════════════════════════════
Write-Step 0 "Prerequisites"

# Refresh PATH so aws / docker picked up from new installs
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" +
            [System.Environment]::GetEnvironmentVariable("Path","User")

foreach ($cmd in @("aws","docker")) {
    if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
        Fail "$cmd not found in PATH. Please install it first."
    }
}

# Verify Docker daemon is running
$dockerInfo = docker info 2>&1
if ($LASTEXITCODE -ne 0) { Fail "Docker daemon is not running. Please start Docker Desktop." }
Write-OK "Docker is running"

# Verify AWS credentials
try {
    $caller = (aws sts get-caller-identity 2>&1 | ConvertFrom-Json)
    $AWS_ACCOUNT_ID = $caller.Account
} catch {
    Fail "AWS CLI not configured. Run 'aws configure' first."
}
Write-OK "AWS account : $AWS_ACCOUNT_ID"

$ECR_BASE = "$AWS_ACCOUNT_ID.dkr.ecr.$Region.amazonaws.com"
$ECR_URI  = "$ECR_BASE/$RepositoryName"
$TAG      = Get-Date -Format 'yyyyMMdd-HHmmss'
$IMAGE_LATEST   = "${ECR_URI}:latest"
$IMAGE_VERSIONED = "${ECR_URI}:${TAG}"

# ═══════════════════════════════════════════════════════════════════════════════
# STEP 1 — ECR Repository
# ═══════════════════════════════════════════════════════════════════════════════
Write-Step 1 "ECR Repository"

$ecrCheck = aws ecr describe-repositories --repository-names $RepositoryName --region $Region 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-OK "ECR repository '$RepositoryName' already exists"
} else {
    Write-Info "Creating ECR repository..."
    aws ecr create-repository `
        --repository-name $RepositoryName `
        --image-scanning-configuration scanOnPush=true `
        --region $Region | Out-Null
    Assert-ExitCode "Failed to create ECR repository"
    Write-OK "ECR repository created"
}
Write-Info "URI: $ECR_URI"

# ═══════════════════════════════════════════════════════════════════════════════
# STEP 2 — Build Docker Image
# ═══════════════════════════════════════════════════════════════════════════════
Write-Step 2 "Docker Build"

if ($SkipBuild) {
    Write-Warn "Skipping build (-SkipBuild was set)."
} else {
    Write-Info "Building linux/amd64 image  →  $IMAGE_LATEST"
    docker build --platform linux/amd64 `
        -t $IMAGE_LATEST `
        -t $IMAGE_VERSIONED `
        .
    Assert-ExitCode "Docker build failed"
    Write-OK "Image built successfully  ($TAG)"
}

# ═══════════════════════════════════════════════════════════════════════════════
# STEP 3 — Push to ECR
# ═══════════════════════════════════════════════════════════════════════════════
Write-Step 3 "Push to ECR"

Write-Info "Authenticating with ECR..."
aws ecr get-login-password --region $Region |
    docker login --username AWS --password-stdin $ECR_BASE
Assert-ExitCode "ECR login failed"
Write-OK "ECR login successful"

if (-not $SkipBuild) {
    Write-Info "Pushing $IMAGE_LATEST ..."
    docker push $IMAGE_LATEST
    Assert-ExitCode "docker push :latest failed"

    docker push $IMAGE_VERSIONED
    Assert-ExitCode "docker push :$TAG failed"
    Write-OK "Image pushed to ECR"
} else {
    Write-Warn "Skipping push (-SkipBuild was set)."
}

if ($SkipDeploy) {
    Write-OK "Done (SkipDeploy). Image is in ECR: $IMAGE_LATEST"
    exit 0
}

# ═══════════════════════════════════════════════════════════════════════════════
# STEP 4 — IAM Role / Instance Profile
# ═══════════════════════════════════════════════════════════════════════════════
Write-Step 4 "IAM Role for EC2"

$roleCheck = aws iam get-role --role-name bmk-ec2-role 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-OK "IAM role 'bmk-ec2-role' already exists"
} else {
    Write-Info "Creating IAM role..."
    $trustDoc = @'
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": { "Service": "ec2.amazonaws.com" },
    "Action": "sts:AssumeRole"
  }]
}
'@
    $trustFile = "$env:TEMP\bmk-ec2-trust.json"
    $trustDoc | Set-Content -Path $trustFile -Encoding UTF8

    aws iam create-role `
        --role-name bmk-ec2-role `
        --assume-role-policy-document "file://$trustFile" | Out-Null
    Assert-ExitCode "Failed to create IAM role"

    foreach ($arn in @(
        "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly",
        "arn:aws:iam::aws:policy/CloudWatchLogsFullAccess"
    )) {
        aws iam attach-role-policy --role-name bmk-ec2-role --policy-arn $arn | Out-Null
    }

    $profCheck = aws iam get-instance-profile --instance-profile-name bmk-ec2-role 2>&1
    if ($LASTEXITCODE -ne 0) {
        aws iam create-instance-profile --instance-profile-name bmk-ec2-role | Out-Null
        aws iam add-role-to-instance-profile `
            --instance-profile-name bmk-ec2-role `
            --role-name bmk-ec2-role | Out-Null
        Write-Info "Waiting 15 s for instance profile to propagate..."
        Start-Sleep -Seconds 15
    }
    Write-OK "IAM role and instance profile created"
}

# ═══════════════════════════════════════════════════════════════════════════════
# STEP 5 — Security Groups
# ═══════════════════════════════════════════════════════════════════════════════
Write-Step 5 "Security Groups"

# ── EC2 SG ──────────────────────────────────────────────────────────────────
$sgRaw = aws ec2 describe-security-groups `
    --filters "Name=group-name,Values=bmk-backend-sg" `
    --region $Region 2>&1 | ConvertFrom-Json

if ($sgRaw.SecurityGroups.Count -gt 0) {
    $SG_ID = $sgRaw.SecurityGroups[0].GroupId
    Write-OK "EC2 security group exists: $SG_ID"
} else {
    Write-Info "Creating EC2 security group..."
    $sgCreate = aws ec2 create-security-group `
        --group-name bmk-backend-sg `
        --description "BusyMumKitchen Backend Security Group" `
        --region $Region 2>&1 | ConvertFrom-Json
    Assert-ExitCode "Failed to create EC2 security group"
    $SG_ID = $sgCreate.GroupId
    Write-OK "EC2 security group created: $SG_ID"

    @(22, 80, 443, 8080) | ForEach-Object {
        aws ec2 authorize-security-group-ingress `
            --group-id $SG_ID `
            --protocol tcp `
            --port $_ `
            --cidr 0.0.0.0/0 `
            --region $Region | Out-Null
    }
    Write-OK "Ingress rules added (22, 80, 443, 8080)"
}

# ── RDS SG (standalone, so EC2 can reach it on 5432) ────────────────────────
$rdsSgRaw = aws ec2 describe-security-groups `
    --filters "Name=group-name,Values=bmk-rds-sg" `
    --region $Region 2>&1 | ConvertFrom-Json

if ($rdsSgRaw.SecurityGroups.Count -gt 0) {
    $RDS_SG_ID = $rdsSgRaw.SecurityGroups[0].GroupId
    Write-OK "RDS security group exists: $RDS_SG_ID"
} else {
    Write-Info "Creating RDS security group..."
    $rdsSgCreate = aws ec2 create-security-group `
        --group-name bmk-rds-sg `
        --description "BusyMumKitchen RDS PostgreSQL Security Group" `
        --region $Region 2>&1 | ConvertFrom-Json
    Assert-ExitCode "Failed to create RDS security group"
    $RDS_SG_ID = $rdsSgCreate.GroupId

    # Allow PostgreSQL from EC2 SG only
    aws ec2 authorize-security-group-ingress `
        --group-id $RDS_SG_ID `
        --protocol tcp `
        --port 5432 `
        --source-group $SG_ID `
        --region $Region | Out-Null
    Write-OK "RDS security group created: $RDS_SG_ID  (5432 open to EC2 SG)"
}

# ═══════════════════════════════════════════════════════════════════════════════
# STEP 6 — RDS PostgreSQL
# ═══════════════════════════════════════════════════════════════════════════════
Write-Step 6 "RDS PostgreSQL"

if ([string]::IsNullOrEmpty($DBPassword)) {
    $DBPassword = New-RandomPassword -Length 24
    Write-Warn "Auto-generated DB password: $DBPassword  ← SAVE THIS!"
}

$rdsRaw = aws rds describe-db-instances `
    --db-instance-identifier $DBInstanceId `
    --region $Region 2>&1

if ($LASTEXITCODE -eq 0) {
    $rdsObj  = ($rdsRaw | ConvertFrom-Json).DBInstances[0]
    $DB_HOST = $rdsObj.Endpoint.Address
    $DB_PORT = $rdsObj.Endpoint.Port
    Write-OK "RDS instance already exists: $DB_HOST`:$DB_PORT"
} else {
    Write-Info "Fetching default subnets for DB subnet group..."
    $subnets = (aws ec2 describe-subnets `
        --filters "Name=defaultForAz,Values=true" `
        --region $Region `
        --query "Subnets[*].SubnetId" `
        --output json 2>&1 | ConvertFrom-Json)

    if ($subnets.Count -lt 2) {
        Fail "Need at least 2 subnets in different AZs for a DB subnet group. Check your VPC."
    }
    Write-Info "Subnets: $($subnets -join ', ')"

    # Create subnet group (ignore error if already exists)
    aws rds create-db-subnet-group `
        --db-subnet-group-name bmk-db-subnet `
        --db-subnet-group-description "BMK DB Subnet Group" `
        --subnet-ids $subnets `
        --region $Region 2>&1 | Out-Null

    Write-Info "Creating RDS instance (this takes 5-10 min)..."
    aws rds create-db-instance `
        --db-instance-identifier $DBInstanceId `
        --db-instance-class db.t3.micro `
        --engine postgres `
        --engine-version "16.3" `
        --master-username $DBUsername `
        --master-user-password $DBPassword `
        --db-name $DBName `
        --allocated-storage 20 `
        --storage-type gp2 `
        --no-publicly-accessible `
        --vpc-security-group-ids $RDS_SG_ID `
        --db-subnet-group-name bmk-db-subnet `
        --backup-retention-period 7 `
        --region $Region | Out-Null
    Assert-ExitCode "Failed to create RDS instance"

    Write-Info "Waiting for RDS to become available..."
    aws rds wait db-instance-available `
        --db-instance-identifier $DBInstanceId `
        --region $Region
    Assert-ExitCode "Timed out waiting for RDS"

    $rdsObj  = (aws rds describe-db-instances `
        --db-instance-identifier $DBInstanceId `
        --region $Region | ConvertFrom-Json).DBInstances[0]
    $DB_HOST = $rdsObj.Endpoint.Address
    $DB_PORT = $rdsObj.Endpoint.Port
    Write-OK "RDS created: $DB_HOST`:$DB_PORT"
}

# Ensure the existing RDS instance uses our dedicated RDS SG
$currentSGs = $rdsObj.VpcSecurityGroups | ForEach-Object { $_.VpcSecurityGroupId }
if ($currentSGs -notcontains $RDS_SG_ID) {
    Write-Info "Attaching bmk-rds-sg to RDS instance..."
    $allSGs = ($currentSGs + $RDS_SG_ID) -join " "
    aws rds modify-db-instance `
        --db-instance-identifier $DBInstanceId `
        --vpc-security-group-ids $allSGs `
        --apply-immediately `
        --region $Region | Out-Null
    Write-OK "RDS security group updated"
}

# ═══════════════════════════════════════════════════════════════════════════════
# STEP 7 — EC2 Key Pair
# ═══════════════════════════════════════════════════════════════════════════════
Write-Step 7 "EC2 Key Pair"

$keyName = "bmk-key"
$keyPath = Join-Path $PWD "$keyName.pem"

$keyCheck = aws ec2 describe-key-pairs --key-names $keyName --region $Region 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-OK "Key pair '$keyName' exists in AWS"
    if (-not (Test-Path $keyPath)) {
        Write-Warn "Local key file not found at $keyPath — SSH steps will be skipped."
        $keyPath = $null
    } else {
        Write-OK "Local PEM: $keyPath"
    }
} else {
    Write-Info "Creating key pair..."
    $keyMat = aws ec2 create-key-pair `
        --key-name $keyName `
        --query "KeyMaterial" `
        --output text `
        --region $Region
    Assert-ExitCode "Failed to create key pair"
    $keyMat | Set-Content -Path $keyPath -Encoding UTF8 -NoNewline
    Write-OK "Key pair saved: $keyPath"
    Write-Warn "Keep this file safe — you cannot download it again!"
}

# ═══════════════════════════════════════════════════════════════════════════════
# STEP 8 — Latest Ubuntu 22.04 AMI
# ═══════════════════════════════════════════════════════════════════════════════
Write-Step 8 "Ubuntu 22.04 AMI"

$AMI_ID = aws ec2 describe-images `
    --owners 099720109477 `
    --filters "Name=name,Values=ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*" `
              "Name=state,Values=available" `
    --query "sort_by(Images, &CreationDate)[-1].ImageId" `
    --output text `
    --region $Region
Assert-ExitCode "Failed to get Ubuntu AMI"
Write-OK "AMI: $AMI_ID"

# ═══════════════════════════════════════════════════════════════════════════════
# STEP 9 — EC2 Instance
# ═══════════════════════════════════════════════════════════════════════════════
Write-Step 9 "EC2 Instance"

$instRaw = aws ec2 describe-instances `
    --filters "Name=tag:Name,Values=$InstanceName" `
              "Name=instance-state-name,Values=running,stopped,pending" `
    --region $Region 2>&1 | ConvertFrom-Json

if ($instRaw.Reservations.Count -gt 0) {
    $inst        = $instRaw.Reservations[0].Instances[0]
    $INSTANCE_ID = $inst.InstanceId
    $EC2_IP      = $inst.PublicIpAddress
    Write-OK "EC2 instance exists: $INSTANCE_ID  ($($inst.State.Name))  IP: $EC2_IP"

    if ($inst.State.Name -eq "stopped") {
        Write-Info "Starting stopped instance..."
        aws ec2 start-instances --instance-ids $INSTANCE_ID --region $Region | Out-Null
        aws ec2 wait instance-running --instance-ids $INSTANCE_ID --region $Region
        $EC2_IP = (aws ec2 describe-instances --instance-ids $INSTANCE_ID --region $Region |
                   ConvertFrom-Json).Reservations[0].Instances[0].PublicIpAddress
        Write-OK "Instance started. IP: $EC2_IP"
    }
} else {
    # Cloud-init user data: installs Docker + Docker Compose plugin on first boot
    $userData = @'
#!/bin/bash
set -e
apt-get update -y
apt-get install -y ca-certificates curl gnupg lsb-release unzip

# Docker
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Let ubuntu user run docker without sudo
usermod -aG docker ubuntu

# AWS CLI v2
curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
unzip -q /tmp/awscliv2.zip -d /tmp
/tmp/aws/install

systemctl enable docker
systemctl start docker
mkdir -p /opt/bmk/deploy
'@

    $udEncoded = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($userData))

    Write-Info "Launching EC2 instance (type: $InstanceType)..."
    $launchRaw = aws ec2 run-instances `
        --image-id $AMI_ID `
        --instance-type $InstanceType `
        --key-name $keyName `
        --security-group-ids $SG_ID `
        --iam-instance-profile Name=bmk-ec2-role `
        --block-device-mappings '[{"DeviceName":"/dev/sda1","Ebs":{"VolumeSize":30,"VolumeType":"gp3"}}]' `
        --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$InstanceName}]" `
        --user-data $udEncoded `
        --region $Region 2>&1 | ConvertFrom-Json
    Assert-ExitCode "Failed to launch EC2 instance"

    $INSTANCE_ID = $launchRaw.Instances[0].InstanceId
    Write-OK "Instance launched: $INSTANCE_ID"

    Write-Info "Waiting for instance to be running..."
    aws ec2 wait instance-running --instance-ids $INSTANCE_ID --region $Region

    $EC2_IP = (aws ec2 describe-instances `
        --instance-ids $INSTANCE_ID `
        --region $Region | ConvertFrom-Json).Reservations[0].Instances[0].PublicIpAddress
    Write-OK "Instance running. Public IP: $EC2_IP"

    Write-Warn "Waiting 60 s for cloud-init / Docker installation to complete..."
    Start-Sleep -Seconds 60
}

# ═══════════════════════════════════════════════════════════════════════════════
# STEP 10 — Generate .env for EC2
# ═══════════════════════════════════════════════════════════════════════════════
Write-Step 10 "Generate .env.aws"

if ([string]::IsNullOrEmpty($JwtSecret)) {
    $JwtSecret = New-RandomSecret -Bytes 64
}

$envContent = @"
# ── Auto-generated by deploy-rds.ps1  $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ──

# ── PostgreSQL (AWS RDS) ────────────────────────────────────────────────────
DB_HOST=$DB_HOST
DB_PORT=$DB_PORT
DB_NAME=$DBName
DB_USERNAME=$DBUsername
DB_PASSWORD=$DBPassword

# ── Redis (local container) ─────────────────────────────────────────────────
REDIS_ENABLED=true
REDIS_HOST=redis
REDIS_PORT=6379

# ── RabbitMQ (local container) ──────────────────────────────────────────────
RABBITMQ_ENABLED=true
RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_USER=guest
RABBITMQ_PASS=guest
RABBITMQ_DEFAULT_USER=guest
RABBITMQ_DEFAULT_PASS=guest

# ── JWT ──────────────────────────────────────────────────────────────────────
JWT_SECRET=$JwtSecret

# ── ECR ──────────────────────────────────────────────────────────────────────
ECR_IMAGE=$ECR_URI

# ── AWS ──────────────────────────────────────────────────────────────────────
AWS_REGION=$Region
AWS_SNS_ENABLED=false
AWS_ACCESS_KEY=
AWS_SECRET_KEY=
AWS_SNS_SENDER_ID=BMKITCHN

# ── OTP ──────────────────────────────────────────────────────────────────────
OTP_DELIVERY_METHOD=EMAIL

# ── Email (Gmail SMTP) ───────────────────────────────────────────────────────
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=

# ── Stripe ───────────────────────────────────────────────────────────────────
STRIPE_SECRET_KEY=sk_test_replace_me
STRIPE_WEBHOOK_SECRET=whsec_replace_me

# ── S3 (optional image storage) ──────────────────────────────────────────────
S3_BUCKET=
S3_FOLDER=bmk-menu
"@

$envFile = Join-Path $PWD ".env.aws"
$envContent | Set-Content -Path $envFile -Encoding UTF8
Write-OK ".env.aws written to: $envFile"
Write-Warn "Edit .env.aws to add Stripe keys, email credentials, etc. before going live."

# ═══════════════════════════════════════════════════════════════════════════════
# STEP 11 — Deploy to EC2 (SCP + SSH)
# ═══════════════════════════════════════════════════════════════════════════════
Write-Step 11 "Deploy to EC2"

if ($null -eq $keyPath -or -not (Test-Path $keyPath)) {
    Write-Warn "PEM key not found locally — skipping automated SSH deploy."
    Write-Warn "Run the following commands manually from a machine that has the key:"
    Write-Host ""
    Write-Host "  # Copy files" -ForegroundColor DarkGray
    Write-Host "  scp -i bmk-key.pem -o StrictHostKeyChecking=no ``" -ForegroundColor Gray
    Write-Host "      deploy/docker-compose.aws.yml ubuntu@${EC2_IP}:/opt/bmk/deploy/docker-compose.yml" -ForegroundColor Gray
    Write-Host "  scp -i bmk-key.pem -o StrictHostKeyChecking=no ``" -ForegroundColor Gray
    Write-Host "      .env.aws ubuntu@${EC2_IP}:/opt/bmk/deploy/.env" -ForegroundColor Gray
    Write-Host ""
    Write-Host "  # Login to ECR on the instance and start" -ForegroundColor DarkGray
    Write-Host "  ssh -i bmk-key.pem ubuntu@${EC2_IP} 'bash -s' << 'EOF'" -ForegroundColor Gray
    Write-Host "  aws ecr get-login-password --region $Region | docker login --username AWS --password-stdin $ECR_BASE" -ForegroundColor Gray
    Write-Host "  cd /opt/bmk/deploy && docker compose pull && docker compose up -d" -ForegroundColor Gray
    Write-Host "  EOF" -ForegroundColor Gray
} else {
    $sshOpts = "-i `"$keyPath`" -o StrictHostKeyChecking=no -o ConnectTimeout=30"
    $scpOpts = "-i `"$keyPath`" -o StrictHostKeyChecking=no -o ConnectTimeout=30"
    $remote  = "ubuntu@$EC2_IP"

    Write-Info "Uploading docker-compose.aws.yml → /opt/bmk/deploy/docker-compose.yml"
    $composeAws = Join-Path $PWD "deploy\docker-compose.aws.yml"
    Invoke-Expression "scp $scpOpts `"$composeAws`" ${remote}:/opt/bmk/deploy/docker-compose.yml"
    Assert-ExitCode "SCP of docker-compose.aws.yml failed"

    Write-Info "Uploading .env.aws → /opt/bmk/deploy/.env"
    Invoke-Expression "scp $scpOpts `"$envFile`" ${remote}:/opt/bmk/deploy/.env"
    Assert-ExitCode "SCP of .env.aws failed"

    # Secure the env file
    Invoke-Expression "ssh $sshOpts $remote 'chmod 600 /opt/bmk/deploy/.env'"

    Write-Info "Logging EC2 into ECR and starting containers..."
    $remoteCmd = @"
set -e
aws ecr get-login-password --region $Region | docker login --username AWS --password-stdin $ECR_BASE
cd /opt/bmk/deploy
docker compose pull
docker compose up -d --remove-orphans
docker compose ps
"@
    $remoteCmd | Invoke-Expression { ssh $sshOpts.Split() $remote 'bash -s' } 2>&1 | Out-Null
    # Use here-string via temp file for cross-platform reliability
    $tmpSh = "$env:TEMP\bmk-deploy.sh"
    $remoteCmd | Set-Content -Path $tmpSh -Encoding UTF8

    Invoke-Expression "scp $scpOpts `"$tmpSh`" ${remote}:/tmp/bmk-deploy.sh"
    Invoke-Expression "ssh $sshOpts $remote 'bash /tmp/bmk-deploy.sh'"
    if ($LASTEXITCODE -ne 0) {
        Write-Warn "Remote deploy script returned non-zero. Check EC2 logs with:"
        Write-Warn "  ssh $sshOpts $remote 'docker compose -f /opt/bmk/deploy/docker-compose.yml logs --tail=50'"
    } else {
        Write-OK "Application started on EC2!"
    }
}

# ═══════════════════════════════════════════════════════════════════════════════
# STEP 12 — Deployment Summary
# ═══════════════════════════════════════════════════════════════════════════════
Write-Step 12 "Deployment Summary"

$summary = @"

╔══════════════════════════════════════════════════════════════════════════════╗
║                    DEPLOYMENT SUMMARY  —  SAVE THIS INFO                    ║
╚══════════════════════════════════════════════════════════════════════════════╝

  [RDS PostgreSQL]
    Instance ID  : $DBInstanceId
    Endpoint     : ${DB_HOST}:${DB_PORT}
    Database     : $DBName
    Username     : $DBUsername
    Password     : $DBPassword

  [EC2 Instance]
    Instance ID  : $INSTANCE_ID
    Public IP    : $EC2_IP
    Type         : $InstanceType
    SSH          : ssh -i "$keyPath" ubuntu@$EC2_IP

  [ECR Repository]
    URI          : $ECR_URI
    Latest tag   : $TAG

  [Security Groups]
    EC2 SG       : $SG_ID
    RDS SG       : $RDS_SG_ID

  [Config Files]
    .env.aws     : $envFile
    PEM key      : $keyPath

──────────────────────────────────────────────────────────────────────────────

  [Health Check URLs]
    App health   : http://${EC2_IP}:8080/api/v1/actuator/health
    Swagger UI   : http://${EC2_IP}:8080/api/v1/swagger-ui.html

  [Useful Commands]
    # SSH to server
    ssh -i "$keyPath" ubuntu@$EC2_IP

    # Tail application logs
    ssh -i "$keyPath" ubuntu@$EC2_IP 'docker compose -f /opt/bmk/deploy/docker-compose.yml logs -f backend'

    # Re-deploy after a new image push
    ssh -i "$keyPath" ubuntu@$EC2_IP 'cd /opt/bmk/deploy && docker compose pull && docker compose up -d'

    # Build + push a fresh image
    .\build-docker.ps1 -Push

──────────────────────────────────────────────────────────────────────────────

  [Remaining Manual Steps]
    1.  Edit .env.aws  →  fill in STRIPE, MAIL, AWS SNS keys if needed
    2.  Re-upload .env:
          scp -i "$keyPath" .env.aws ubuntu@${EC2_IP}:/opt/bmk/deploy/.env
          ssh  -i "$keyPath" ubuntu@$EC2_IP 'cd /opt/bmk/deploy && docker compose up -d'
    3.  Point your domain / load balancer to $EC2_IP:8080
    4.  Restrict SSH (port 22) to your IP only in security group $SG_ID

══════════════════════════════════════════════════════════════════════════════
"@

Write-Host $summary -ForegroundColor Cyan
$summary | Out-File -FilePath (Join-Path $PWD "DEPLOYMENT_SUMMARY.txt") -Encoding UTF8
Write-OK "Summary saved → DEPLOYMENT_SUMMARY.txt"

Write-Host "`n✅  All done!  Your BusyMumKitchen backend is deploying on EC2 → RDS.`n" -ForegroundColor Green

