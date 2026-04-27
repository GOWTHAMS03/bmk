# BusyMumKitchen AWS Deployment Script
# Run as Administrator
# Prerequisites: AWS CLI v2 and Docker installed

param(
    [string]$Region = "ap-south-1",
    [string]$DBPassword = ""
)

function Write-Success  { Write-Host "[SUCCESS] $($args[0])" -ForegroundColor Green }
function Write-Error-Msg { Write-Host "[ERROR] $($args[0])" -ForegroundColor Red }
function Write-Warning-Msg { Write-Host "[WARNING] $($args[0])" -ForegroundColor Yellow }
function Write-Info-Msg { Write-Host "[INFO] $($args[0])" -ForegroundColor Cyan }

Write-Host "`n[STARTING] BusyMumKitchen AWS Infrastructure Setup`n" -ForegroundColor Magenta

# Check if running as Administrator
$currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object System.Security.Principal.WindowsPrincipal($currentUser)
$isAdmin = $principal.IsInRole([System.Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Error-Msg "This script must be run as Administrator"
    exit 1
}

# Check AWS CLI
Write-Info-Msg "Checking AWS CLI..."
$awsVersion = aws --version 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Error-Msg "AWS CLI not found. Please install AWS CLI v2 first."
    exit 1
}
Write-Success "AWS CLI: $awsVersion"

# Test AWS credentials
Write-Info-Msg "Testing AWS credentials..."
$caller = aws sts get-caller-identity --output json 2>&1 | ConvertFrom-Json
if ($LASTEXITCODE -ne 0) {
    Write-Warning-Msg "AWS CLI not configured. Running 'aws configure'..."
    aws configure
} else {
    Write-Success "AWS Account: $($caller.Account)"
}

$AWS_ACCOUNT_ID = (aws sts get-caller-identity --query Account --output text)
Write-Success "AWS Account ID: $AWS_ACCOUNT_ID"

# Generate strong password if not provided
if ([string]::IsNullOrEmpty($DBPassword)) {
    Write-Info-Msg "Generating strong database password..."
    $DBPassword = -join ((33..126) | Get-Random -Count 32 | foreach-object { [char]$_ })
    Write-Success "Generated DB Password: $DBPassword"
}

# Step 1: Create EC2 Key Pair
Write-Host "`n[STEP 1] Creating EC2 Key Pair" -ForegroundColor Magenta
$keyName = "bmk-key"
$keyPath = "$PSScriptRoot\$keyName.pem"

$keyCheck = aws ec2 describe-key-pairs --key-names $keyName --region $Region 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Info-Msg "Key pair '$keyName' already exists"
} else {
    Write-Info-Msg "Creating key pair..."
    $keyOutput = aws ec2 create-key-pair --key-name $keyName --query 'KeyMaterial' --output text --region $Region
    $keyOutput | Set-Content -Path $keyPath -Encoding UTF8
    Write-Success "Key pair created: $keyPath"
}

# Step 2: Create Security Group
Write-Host "`n[STEP 2] Creating Security Group" -ForegroundColor Magenta
$sgCheck = aws ec2 describe-security-groups --filters "Name=group-name,Values=bmk-backend-sg" --region $Region 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Info-Msg "Security group 'bmk-backend-sg' already exists"
} else {
    Write-Info-Msg "Creating security group..."
    $sgCreate = aws ec2 create-security-group --group-name bmk-backend-sg --description "BusyMumKitchen Backend" --region $Region --output json 2>&1 | ConvertFrom-Json
    $SG_ID = $sgCreate.GroupId
    Write-Success "Security group created: $SG_ID"

    # Add ingress rules
    Write-Info-Msg "Adding security group rules..."
    aws ec2 authorize-security-group-ingress --group-id $SG_ID --protocol tcp --port 22 --cidr 0.0.0.0/0 --region $Region 2>&1 | Out-Null
    aws ec2 authorize-security-group-ingress --group-id $SG_ID --protocol tcp --port 80 --cidr 0.0.0.0/0 --region $Region 2>&1 | Out-Null
    aws ec2 authorize-security-group-ingress --group-id $SG_ID --protocol tcp --port 443 --cidr 0.0.0.0/0 --region $Region 2>&1 | Out-Null
    aws ec2 authorize-security-group-ingress --group-id $SG_ID --protocol tcp --port 8080 --cidr 0.0.0.0/0 --region $Region 2>&1 | Out-Null
    Write-Success "Security group rules added"
}

# Step 3: Create RDS Database
Write-Host "`n[STEP 3] Creating RDS PostgreSQL Database" -ForegroundColor Magenta
$DBInstanceId = "bmk-postgres"
$DBName = "busymumkitchen"
$DBUsername = "bmk_user"

$rdsCheck = aws rds describe-db-instances --db-instance-identifier $DBInstanceId --region $Region 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Info-Msg "RDS instance '$DBInstanceId' already exists"
    $rdsInfo = aws rds describe-db-instances --db-instance-identifier $DBInstanceId --region $Region --output json | ConvertFrom-Json
    $DB_HOST = $rdsInfo.DBInstances[0].Endpoint.Address
    $DB_PORT = $rdsInfo.DBInstances[0].Endpoint.Port
} else {
    Write-Info-Msg "Creating RDS database (this may take 10+ minutes)..."

    # Get default subnets
    $subnets = aws ec2 describe-subnets --filters "Name=defaultForAz,Values=true" --region $Region --query "Subnets[*].SubnetId" --output text
    $subnetList = $subnets -split '\s+'

    # Create DB subnet group
    try {
        aws rds create-db-subnet-group --db-subnet-group-name bmk-db-subnet --db-subnet-group-description "BMK DB Subnet" --subnet-ids $subnetList --region $Region 2>&1 | Out-Null
    } catch { }

    # Create RDS instance
    aws rds create-db-instance `
        --db-instance-identifier $DBInstanceId `
        --db-instance-class db.t3.micro `
        --engine postgres `
        --engine-version 16.3 `
        --master-username $DBUsername `
        --master-user-password $DBPassword `
        --db-name $DBName `
        --allocated-storage 20 `
        --storage-type gp2 `
        --no-publicly-accessible `
        --db-subnet-group-name bmk-db-subnet `
        --backup-retention-period 7 `
        --region $Region 2>&1 | Out-Null

    Write-Info-Msg "Waiting for RDS to be available..."
    aws rds wait db-instance-available --db-instance-identifier $DBInstanceId --region $Region

    $rdsInfo = aws rds describe-db-instances --db-instance-identifier $DBInstanceId --region $Region --output json | ConvertFrom-Json
    $DB_HOST = $rdsInfo.DBInstances[0].Endpoint.Address
    $DB_PORT = $rdsInfo.DBInstances[0].Endpoint.Port
    Write-Success "RDS database created: $DB_HOST"
}

# Step 4: Create IAM Role
Write-Host "`n[STEP 4] Creating IAM Role for EC2" -ForegroundColor Magenta
$roleCheck = aws iam get-role --role-name bmk-ec2-role 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Info-Msg "IAM role 'bmk-ec2-role' already exists"
} else {
    Write-Info-Msg "Creating IAM role..."
    $trustPolicy = '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ec2.amazonaws.com"},"Action":"sts:AssumeRole"}]}'
    $trustPolicy | Set-Content -Path "$env:TEMP\ec2-trust.json" -Encoding UTF8

    aws iam create-role --role-name bmk-ec2-role --assume-role-policy-document "file://$env:TEMP\ec2-trust.json" 2>&1 | Out-Null
    aws iam attach-role-policy --role-name bmk-ec2-role --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly 2>&1 | Out-Null
    aws iam attach-role-policy --role-name bmk-ec2-role --policy-arn arn:aws:iam::aws:policy/CloudWatchLogsFullAccess 2>&1 | Out-Null
    aws iam create-instance-profile --instance-profile-name bmk-ec2-role 2>&1 | Out-Null
    aws iam add-role-to-instance-profile --instance-profile-name bmk-ec2-role --role-name bmk-ec2-role 2>&1 | Out-Null
    Write-Success "IAM role created"
    Start-Sleep -Seconds 5
}

# Step 5: Create ECR Repository
Write-Host "`n[STEP 5] Creating ECR Repository" -ForegroundColor Magenta
$ecrCheck = aws ecr describe-repositories --repository-names bmk-backend --region $Region 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Info-Msg "ECR repository 'bmk-backend' already exists"
} else {
    Write-Info-Msg "Creating ECR repository..."
    aws ecr create-repository --repository-name bmk-backend --image-scanning-configuration scanOnPush=true --region $Region 2>&1 | Out-Null
    Write-Success "ECR repository created"
}

$ECR_URI = "$AWS_ACCOUNT_ID.dkr.ecr.$Region.amazonaws.com/bmk-backend"

# Step 6: Launch EC2 Instance
Write-Host "`n[STEP 6] Launching EC2 Instance" -ForegroundColor Magenta
$InstanceName = "bmk-backend"
$instanceCheck = aws ec2 describe-instances --filters "Name=tag:Name,Values=$InstanceName" "Name=instance-state-name,Values=running,stopped,pending" --region $Region 2>&1 | ConvertFrom-Json
if ($instanceCheck.Reservations.Count -gt 0) {
    $INSTANCE_ID = $instanceCheck.Reservations[0].Instances[0].InstanceId
    $EC2_IP = $instanceCheck.Reservations[0].Instances[0].PublicIpAddress
    Write-Info-Msg "Instance already exists: $INSTANCE_ID"
} else {
    Write-Info-Msg "Launching EC2 instance..."
    $AMI_ID = aws ec2 describe-images --owners 099720109477 --filters 'Name=name,Values=ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*' --query 'sort_by(Images, `"&CreationDate`)[-1].ImageId' --output text --region $Region

    $blockDevice = '[{"DeviceName":"/dev/sda1","Ebs":{"VolumeSize":30,"VolumeType":"gp3"}}]'
    $SG_ID = (aws ec2 describe-security-groups --filters "Name=group-name,Values=bmk-backend-sg" --region $Region --query 'SecurityGroups[0].GroupId' --output text)

    $instance = aws ec2 run-instances --image-id $AMI_ID --instance-type t3.medium --key-name bmk-key --security-group-ids $SG_ID --block-device-mappings $blockDevice --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$InstanceName}]" --iam-instance-profile Name=bmk-ec2-role --region $Region --output json 2>&1 | ConvertFrom-Json

    $INSTANCE_ID = $instance.Instances[0].InstanceId
    Write-Success "Instance launched: $INSTANCE_ID"

    Write-Info-Msg "Waiting for instance to start..."
    aws ec2 wait instance-running --instance-ids $INSTANCE_ID --region $Region
    Start-Sleep -Seconds 30

    $instanceInfo = aws ec2 describe-instances --instance-ids $INSTANCE_ID --region $Region --output json | ConvertFrom-Json
    $EC2_IP = $instanceInfo.Reservations[0].Instances[0].PublicIpAddress
}

Write-Success "Instance IP: $EC2_IP"

# Step 7: Create .env.aws file
Write-Host "`n[STEP 7] Creating Environment Configuration" -ForegroundColor Magenta
$envContent = @"
# Database
DB_HOST=$DB_HOST
DB_PORT=$DB_PORT
DB_NAME=$DBName
DB_USERNAME=$DBUsername
DB_PASSWORD=$DBPassword

# Redis
REDIS_ENABLED=true
REDIS_HOST=redis
REDIS_PORT=6379

# RabbitMQ
RABBITMQ_ENABLED=true
RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_DEFAULT_USER=guest
RABBITMQ_DEFAULT_PASS=guest

# Server
SERVER_PORT=8080
ECR_IMAGE=$ECR_URI

# JWT
JWT_SECRET=$(openssl rand -base64 64 2>$null)

# AWS
AWS_SNS_ENABLED=false
OTP_DELIVERY_METHOD=EMAIL
"@

$envContent | Set-Content -Path "$PSScriptRoot\.env.aws" -Encoding UTF8
Write-Success "Environment file created: .env.aws"

# Create Deployment Summary
$summary = @"
========== DEPLOYMENT SUMMARY ==========

DATABASE (RDS PostgreSQL)
  Instance ID:  $DBInstanceId
  Endpoint:     $DB_HOST`:$DB_PORT
  Database:     $DBName
  Username:     $DBUsername
  Password:     $DBPassword

EC2 INSTANCE
  Instance ID:  $INSTANCE_ID
  IP Address:   $EC2_IP
  Key Pair:     $keyName
  SSH Key:      $keyPath

SECURITY
  EC2 SG ID:    $SG_ID
  ECR URI:      $ECR_URI

========== NEXT STEPS ==========

1. Build Docker image:
   cd $PSScriptRoot
   .\build-docker.ps1 -Push

2. Deploy to EC2:
   .\deploy-to-ec2.ps1

3. SSH into EC2:
   ssh -i $keyPath ubuntu@$EC2_IP

4. View logs:
   ssh -i $keyPath ubuntu@$EC2_IP "cd /opt/bmk/deploy && docker compose logs -f"

========== IMPORTANT ==========

- Save .env.aws file securely
- Save $keyName key file securely
- Database password: $DBPassword
- Do not commit these files to Git!

"@

$summary | Set-Content -Path "$PSScriptRoot\DEPLOYMENT_SUMMARY.txt" -Encoding UTF8
Write-Success "Summary saved to: DEPLOYMENT_SUMMARY.txt"

Write-Host "`n[COMPLETE] AWS Infrastructure Setup Finished!`n" -ForegroundColor Green
Write-Output $summary

