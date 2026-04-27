# BusyMumKitchen AWS Deployment Script for PowerShell
# Run as Administrator
# Prerequisites: AWS CLI v2 installed, Docker installed

param(
    [string]$Region = "ap-south-1",
    [string]$DBInstanceId = "bmk-postgres",
    [string]$DBName = "busymumkitchen",
    [string]$DBUsername = "bmk_user",
    [string]$DBPassword = "BMKSecure#123",
    # Default to a Free Tier eligible instance type to avoid InvalidParameterCombination errors
    # (users can still pass a different type when invoking the script)
    [string]$InstanceType = "t3.micro",
    [string]$InstanceName = "bmk-backend"
)

# Colors for output
function Write-Success { Write-Host "[SUCCESS] $($args[0])" -ForegroundColor Green }
function Write-Error-Custom { Write-Host "[ERROR] $($args[0])" -ForegroundColor Red }
function Write-Warning-Custom { Write-Host "[WARNING] $($args[0])" -ForegroundColor Yellow }
function Write-Info { Write-Host "[INFO] $($args[0])" -ForegroundColor Cyan }
function Fail { param($msg) Write-Error-Custom $msg; exit 1 }

Write-Host @"
==============================================================
  BusyMumKitchen AWS Deployment Script

  This script will set up the complete AWS infrastructure including:
    - AWS CLI Configuration
    - RDS PostgreSQL Database
    - EC2 Instance (t3.medium)
    - Security Groups
    - IAM Roles
    - ECR Repository
==============================================================
"@

# Get AWS account ID early (required for ECR URI)
Write-Info "Getting AWS account information..."
try {
    $caller = aws sts get-caller-identity 2>&1 | ConvertFrom-Json
    $AWS_ACCOUNT_ID = $caller.Account
    Write-Success "AWS Account: $AWS_ACCOUNT_ID"
} catch {
    Write-Error-Custom "Failed to get AWS account ID. Make sure AWS CLI is configured."
    exit 1
}

$summary = @"

====================== DEPLOYMENT SUMMARY - SAVE THIS INFO ======================

INFRASTRUCTURE CREATED/CONFIGURED:

  RDS PostgreSQL Database:
    Instance ID: $DBInstanceId
    Endpoint:    $DB_HOST`:$DB_PORT
    Database:    $DBName
    Username:    $DBUsername
    Password:    $DBPassword  <-- SAVE THIS

  EC2 INSTANCE:
    Instance ID: $INSTANCE_ID
    IP Address:  $EC2_IP
    Instance Type: $InstanceType
    Key Pair:    $keyName
    SSH Key File: $keyPath

  SECURITY GROUPS:
    EC2 SG: $SG_ID
    RDS SG: $RDS_SG

  ECR REPOSITORY:
    URI: $ECR_URI
    Region: $Region
    AWS Account: $AWS_ACCOUNT_ID

NEXT STEPS:
  1) Update .env.aws with Stripe/mail/SNS keys as needed
  2) Build & push Docker image to ECR (see commands below)

     aws ecr get-login-password --region $Region |
       docker login --username AWS --password-stdin $ECR_URI
     docker build --platform linux/amd64 -t $ECR_URI .
     docker push $ECR_URI

  3) SSH to EC2 and deploy:
     ssh -i "$keyPath" ubuntu@${EC2_IP}
     mkdir -p /opt/bmk/deploy
     scp -i "$keyPath" docker-compose.yml ubuntu@${EC2_IP}:/opt/bmk/deploy/
     scp -i "$keyPath" .env.aws ubuntu@${EC2_IP}:/opt/bmk/deploy/.env
     chmod 600 /opt/bmk/deploy/.env
     cd /opt/bmk/deploy && docker compose pull && docker compose up -d

HEALTH CHECK:
  App: http://${EC2_IP}:8080/api/v1/actuator/health
  Docs: http://${EC2_IP}:8080/swagger-ui.html

CONFIGURATION FILES SAVED:
  - .env.aws
  - bmk-key-fixed.pem (SSH private key)

================================================================================
"@

Write-Info "Checking if RDS instance exists..."
$rdsExists = aws rds describe-db-instances --db-instance-identifier $DBInstanceId --region $Region 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Success "RDS instance '$DBInstanceId' already exists"
    $rdsInfo = aws rds describe-db-instances --db-instance-identifier $DBInstanceId --region $Region --query "DBInstances[0]" | ConvertFrom-Json
    $DB_HOST = $rdsInfo.Endpoint.Address
    $DB_PORT = $rdsInfo.Endpoint.Port
    Write-Info "Database Endpoint: $DB_HOST`:$DB_PORT"
} else {
    Write-Info "Creating RDS instance..."

    # Create DB subnet group
    Write-Info "Creating DB subnet group..."
    $subnets = aws ec2 describe-subnets `
        --filters "Name=defaultForAz,Values=true" `
        --region $Region `
        --query "Subnets[*].SubnetId" --output text

    $subnetList = $subnets -split '\s+'

    try {
        $subnetGroup = aws rds create-db-subnet-group `
            --db-subnet-group-name bmk-db-subnet `
            --db-subnet-group-description "BMK DB Subnet Group" `
            --subnet-ids $subnetList `
            --region $Region
        Write-Success "DB subnet group created"
    } catch {
        Write-Warning-Custom "DB subnet group might already exist, continuing..."
    }

    # Create RDS instance
    Write-Info "Creating PostgreSQL RDS instance (this may take 5-10 minutes)..."
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
        --region $Region

    Write-Info "Waiting for RDS instance to be available (this may take 5-10 minutes)..."
    aws rds wait db-instance-available `
        --db-instance-identifier $DBInstanceId `
        --region $Region

    Write-Success "RDS instance created and available!"

    # Get endpoint
    $rdsInfo = aws rds describe-db-instances --db-instance-identifier $DBInstanceId --region $Region --query "DBInstances[0]" | ConvertFrom-Json
    $DB_HOST = $rdsInfo.Endpoint.Address
    $DB_PORT = $rdsInfo.Endpoint.Port
    Write-Info "Database Endpoint: $DB_HOST`:$DB_PORT"
}

# Step 4: Create/Check Security Group
Write-Host "`n▶ STEP 4: Create Security Group" -ForegroundColor Magenta
Write-Host "================================" -ForegroundColor Magenta

Write-Info "Checking for existing security group..."
$sgCheck = aws ec2 describe-security-groups --filters "Name=group-name,Values=bmk-backend-sg" --region $Region 2>&1
if ($LASTEXITCODE -eq 0) {
    $sgInfo = $sgCheck | ConvertFrom-Json
    $SG_ID = $sgInfo.SecurityGroups[0].GroupId
    Write-Success "Security group already exists: $SG_ID"
} else {
    Write-Info "Creating security group..."
    $sgCreate = aws ec2 create-security-group `
        --group-name bmk-backend-sg `
        --description "BusyMumKitchen Backend Security Group" `
        --region $Region | ConvertFrom-Json

    $SG_ID = $sgCreate.GroupId
    Write-Success "Security group created: $SG_ID"

    # Add ingress rules
    Write-Info "Adding SSH access rule..."
    aws ec2 authorize-security-group-ingress `
        --group-id $SG_ID `
        --protocol tcp `
        --port 22 `
        --cidr 0.0.0.0/0 `
        --region $Region

    Write-Info "Adding HTTP access rule..."
    aws ec2 authorize-security-group-ingress `
        --group-id $SG_ID `
        --protocol tcp `
        --port 80 `
        --cidr 0.0.0.0/0 `
        --region $Region

    Write-Info "Adding HTTPS access rule..."
    aws ec2 authorize-security-group-ingress `
        --group-id $SG_ID `
        --protocol tcp `
        --port 443 `
        --cidr 0.0.0.0/0 `
        --region $Region

    Write-Info "Adding app port (8080) access rule..."
    aws ec2 authorize-security-group-ingress `
        --group-id $SG_ID `
        --protocol tcp `
        --port 8080 `
        --cidr 0.0.0.0/0 `
        --region $Region

    Write-Success "Security group rules configured"
}

# Step 5: Create/Check EC2 Key Pair
Write-Host "`n▶ STEP 5: Create EC2 Key Pair" -ForegroundColor Magenta
Write-Host "==============================" -ForegroundColor Magenta

$keyName = "bmk-key"
$keyPath = "$PWD\$keyName.pem"

Write-Info "Checking for existing key pair..."
$keyCheck = aws ec2 describe-key-pairs --key-names $keyName --region $Region 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Success "Key pair '$keyName' already exists"
    if (Test-Path $keyPath) {
        Write-Success "Local key file exists: $keyPath"
    } else {
        Write-Warning-Custom "Local key file not found! You won't be able to SSH to the instance"
    }
} else {
    Write-Info "Creating new key pair..."
    $keyOutput = aws ec2 create-key-pair `
        --key-name $keyName `
        --query "KeyMaterial" `
        --output text `
        --region $Region

    # Write private key without UTF-8 BOM to avoid SSH "invalid format" issues on Windows
    [System.IO.File]::WriteAllText($keyPath, $keyOutput, (New-Object System.Text.UTF8Encoding($false)))
    # Make file read-only (Windows). SSH on Windows ignores unix perms but keep file protected.
    attrib +R $keyPath
    Write-Success "Key pair created: $keyPath"
    Write-Warning-Custom "IMPORTANT: Keep this file secure! You'll need it to SSH to the instance"
}

# Step 6: Get Latest Ubuntu AMI
Write-Host "`n▶ STEP 6: Getting Latest Ubuntu AMI" -ForegroundColor Magenta
Write-Host "====================================" -ForegroundColor Magenta

Write-Info "Fetching latest Ubuntu 22.04 AMI..."
$filter1 = "Name=name,Values=ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"
$filter2 = "Name=state,Values=available"
$query = 'sort_by(Images, &CreationDate)[-1].ImageId'
$AMI_ID = & aws ec2 describe-images --owners 099720109477 --filters $filter1 $filter2 --query $query --output text --region $Region

Write-Success "Latest Ubuntu AMI: $AMI_ID"

# Step 6b: Create IAM Role / Instance Profile for EC2  (ECR read + CloudWatch logs)
Write-Host "`n▶ STEP 6b: Create IAM Instance Profile for EC2" -ForegroundColor Magenta
Write-Host "================================================" -ForegroundColor Magenta

$EC2_ROLE_NAME = "bmk-ec2-role"
$EC2_PROFILE_NAME = "bmk-ec2-role"

# Trust policy that lets EC2 assume this role
$trustPolicy = '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ec2.amazonaws.com"},"Action":"sts:AssumeRole"}]}'
$trustPolicyFile = [System.IO.Path]::GetTempFileName() + ".json"
[System.IO.File]::WriteAllText($trustPolicyFile, $trustPolicy, (New-Object System.Text.UTF8Encoding($false)))

Write-Info "Checking for existing IAM role '$EC2_ROLE_NAME'..."
$roleCheck = aws iam get-role --role-name $EC2_ROLE_NAME --region $Region 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Info "Creating IAM role '$EC2_ROLE_NAME'..."
    aws iam create-role `
        --role-name $EC2_ROLE_NAME `
        --assume-role-policy-document "file://$trustPolicyFile" `
        --description "EC2 role for BusyMumKitchen — ECR read and CloudWatch logs" 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Warning-Custom "Failed to create IAM role. It may already exist or you may lack permissions."
    } else {
        Write-Success "IAM role '$EC2_ROLE_NAME' created"
    }
} else {
    Write-Success "IAM role '$EC2_ROLE_NAME' already exists"
}
Remove-Item $trustPolicyFile -ErrorAction SilentlyContinue

# Attach required policies
$policiesToAttach = @(
    "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly",
    "arn:aws:iam::aws:policy/CloudWatchLogsFullAccess"
)
foreach ($policyArn in $policiesToAttach) {
    $policyName = $policyArn.Split('/')[-1]
    Write-Info "Attaching policy: $policyName"
    aws iam attach-role-policy --role-name $EC2_ROLE_NAME --policy-arn $policyArn 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Warning-Custom "Policy '$policyName' may already be attached, continuing..."
    } else {
        Write-Success "Policy '$policyName' attached"
    }
}

# Create instance profile (EC2 needs a profile wrapping the role)
Write-Info "Checking for existing instance profile '$EC2_PROFILE_NAME'..."
$profileCheck = aws iam get-instance-profile --instance-profile-name $EC2_PROFILE_NAME 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Info "Creating instance profile '$EC2_PROFILE_NAME'..."
    aws iam create-instance-profile --instance-profile-name $EC2_PROFILE_NAME 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Warning-Custom "Failed to create instance profile (may already exist). Continuing..."
    } else {
        Write-Success "Instance profile '$EC2_PROFILE_NAME' created"
        # Add the role to the profile
        aws iam add-role-to-instance-profile --instance-profile-name $EC2_PROFILE_NAME --role-name $EC2_ROLE_NAME 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Warning-Custom "Failed to add role to instance profile (may already be added). Continuing..."
        } else {
            Write-Success "Role '$EC2_ROLE_NAME' added to instance profile"
            # Wait a moment for IAM propagation
            Write-Info "Waiting 10 seconds for IAM propagation..."
            Start-Sleep -Seconds 10
        }
    }
} else {
    Write-Success "Instance profile '$EC2_PROFILE_NAME' already exists"
    # Ensure role is in the profile
    $profileObj = $profileCheck | ConvertFrom-Json
    $rolesInProfile = $profileObj.InstanceProfile.Roles | ForEach-Object { $_.RoleName }
    if ($rolesInProfile -notcontains $EC2_ROLE_NAME) {
        aws iam add-role-to-instance-profile --instance-profile-name $EC2_PROFILE_NAME --role-name $EC2_ROLE_NAME 2>&1 | Out-Null
        Write-Info "Role added to existing instance profile"
    }
}

# Step 7: Launch/Check EC2 Instance
Write-Host "`n▶ STEP 7: Launch EC2 Instance" -ForegroundColor Magenta
Write-Host "==============================" -ForegroundColor Magenta

Write-Info "Checking for existing EC2 instance..."
$instanceCheck = aws ec2 describe-instances `
    --filters "Name=tag:Name,Values=$InstanceName" "Name=instance-state-name,Values=running,stopped,pending,stopping,shutting-down" `
    --region $Region 2>&1 | ConvertFrom-Json

if ($instanceCheck.Reservations.Count -gt 0) {
    $existingInstance = $instanceCheck.Reservations[0].Instances[0]
    $INSTANCE_ID = $existingInstance.InstanceId
    $EC2_IP = $existingInstance.PublicIpAddress
    $EC2_STATUS = $existingInstance.State.Name
    Write-Success "EC2 instance found: $INSTANCE_ID (Status: $EC2_STATUS)"
    Write-Info "Public IP: $EC2_IP"
} else {
    Write-Info "Launching new EC2 instance (this may take 2-3 minutes)..."

    # Write block-device-mappings JSON to a temp file so PowerShell does not strip inner double quotes
    $blockJsonFile = [System.IO.Path]::GetTempFileName() + ".json"
    $blockJson = '[{"DeviceName":"/dev/sda1","Ebs":{"VolumeSize":30,"VolumeType":"gp3"}}]'
    # Write UTF-8 without BOM to avoid AWS CLI parsing errors caused by BOM bytes
    [System.IO.File]::WriteAllText($blockJsonFile, $blockJson, (New-Object System.Text.UTF8Encoding($false)))

    # Run the launch and capture raw output so we can detect errors and parse safely
    # Try to launch instance. If the chosen instance type is not eligible (e.g. not Free Tier)
    # retry once with a Free Tier eligible instance type (t3.micro).
    $attempts = @($InstanceType, 't3.micro') | Select-Object -Unique
    $runOutput = $null
    $exit = 1
    foreach ($tryType in $attempts) {
        Write-Info "Attempting to launch EC2 instance with type: $tryType"
        $runCmd = @(
            'ec2', 'run-instances',
            '--image-id', $AMI_ID,
            '--instance-type', $tryType,
            '--key-name', $keyName,
            '--security-group-ids', $SG_ID,
            '--block-device-mappings', "file://$blockJsonFile",
            '--tag-specifications', "ResourceType=instance,Tags=[{Key=Name,Value=$InstanceName}]",
            '--iam-instance-profile', 'Name=bmk-ec2-role',
            '--region', $Region
        )

        Write-Info "Running AWS CLI: aws $($runCmd -join ' ')"
        $runOutput = aws @runCmd 2>&1
        $exit = $LASTEXITCODE

        if ($exit -eq 0) {
            # success
            $InstanceType = $tryType
            break
        }

        # If we get an InvalidParameterCombination / Free Tier eligibility error, try next candidate
        if ($runOutput -match 'InvalidParameterCombination' -or $runOutput -match 'not eligible for Free Tier' -or $runOutput -match 'free-tier-eligible') {
            Write-Warning-Custom "Instance type '$tryType' not eligible or invalid. Will retry with a Free Tier eligible type if available."
            continue
        } else {
            # non-eligibility error; break and let existing fallback logic handle it
            break
        }
    }

    # remove temp file
    Remove-Item $blockJsonFile -ErrorAction SilentlyContinue

    if ($exit -ne 0) {
        Write-Error-Custom "Failed to run 'aws ec2 run-instances'. AWS CLI returned exit code $exit and output:`n$runOutput"
        Write-Info "Attempting to find an instance by tag '$InstanceName' as a fallback..."
        # Try to find an instance by tag (maybe creation succeeded but parsing failed)
        $fallback = aws ec2 describe-instances --filters "Name=tag:Name,Values=$InstanceName" "Name=instance-state-name,Values=pending,running" --region $Region 2>&1
        try {
            $fallbackObj = $fallback | ConvertFrom-Json
            if ($fallbackObj.Reservations.Count -gt 0) {
                $inst = $fallbackObj.Reservations[0].Instances[0]
                $INSTANCE_ID = $inst.InstanceId
                $EC2_IP = $inst.PublicIpAddress
                Write-Warning-Custom "Found existing/new instance by tag: $INSTANCE_ID ($EC2_IP)"
            } else {
                Fail "No instance found. Inspect AWS CLI output above and try again."
            }
        } catch {
            Fail "Unable to parse AWS CLI fallback output. Original error:`n$runOutput"
        }
    } else {
        # Try to parse JSON result
        try {
            $instanceLaunch = $runOutput | ConvertFrom-Json
        } catch {
            Write-Warning-Custom "AWS CLI returned non-JSON output even though exit code was 0. Output:`n$runOutput"
            # Attempt fallback describe by tag
            $fallbackObj = aws ec2 describe-instances --filters "Name=tag:Name,Values=$InstanceName" "Name=instance-state-name,Values=pending,running" --region $Region 2>&1 | ConvertFrom-Json
            if ($fallbackObj.Reservations.Count -gt 0) {
                $inst = $fallbackObj.Reservations[0].Instances[0]
                $INSTANCE_ID = $inst.InstanceId
                $EC2_IP = $inst.PublicIpAddress
                Write-Warning-Custom "Found instance by tag: $INSTANCE_ID ($EC2_IP)"
            } else {
                Fail "Cannot determine launched instance. AWS output:`n$runOutput"
            }
        }

        if ($null -ne $instanceLaunch) {
            if ($instanceLaunch.Instances.Count -gt 0) {
                $INSTANCE_ID = $instanceLaunch.Instances[0].InstanceId
                Write-Success "Instance launched: $INSTANCE_ID"
            } else {
                Write-Warning-Custom "AWS run-instances returned JSON but no Instances[] element. Falling back to describe-instances."
                $fallbackObj = aws ec2 describe-instances --filters "Name=tag:Name,Values=$InstanceName" "Name=instance-state-name,Values=pending,running" --region $Region 2>&1 | ConvertFrom-Json
                if ($fallbackObj.Reservations.Count -gt 0) {
                    $inst = $fallbackObj.Reservations[0].Instances[0]
                    $INSTANCE_ID = $inst.InstanceId
                    $EC2_IP = $inst.PublicIpAddress
                    Write-Warning-Custom "Found instance by tag: $INSTANCE_ID ($EC2_IP)"
                } else {
                    Fail "Cannot determine launched instance from AWS outputs."
                }
            }
        }

        if (-not [string]::IsNullOrEmpty($INSTANCE_ID)) {
            Write-Info "Waiting for instance to be running..."
            aws ec2 wait instance-running `
                --instance-ids $INSTANCE_ID `
                --region $Region

            $instanceInfo = aws ec2 describe-instances `
                --instance-ids $INSTANCE_ID `
                --region $Region | ConvertFrom-Json

            $EC2_IP = $instanceInfo.Reservations[0].Instances[0].PublicIpAddress
            Write-Success "Instance is running!"
            Write-Info "Public IP: $EC2_IP"

            Write-Warning-Custom "Waiting 30 seconds for instance to fully initialize..."
            Start-Sleep -Seconds 30
        }
    }
}

# Step 8: Create ECR Repository
Write-Host "`n▶ STEP 8: Create ECR Repository" -ForegroundColor Magenta
Write-Host "================================" -ForegroundColor Magenta

Write-Info "Checking for existing ECR repository..."
$ecrCheck = aws ecr describe-repositories --repository-names bmk-backend --region $Region 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Success "ECR repository 'bmk-backend' already exists"
} else {
    Write-Info "Creating ECR repository..."
    aws ecr create-repository `
        --repository-name bmk-backend `
        --image-scanning-configuration scanOnPush=true `
        --region $Region
    Write-Success "ECR repository created"
}

$ECR_URI = "$AWS_ACCOUNT_ID.dkr.ecr.$Region.amazonaws.com/bmk-backend"
Write-Info "ECR URI: $ECR_URI"

# Step 9: Configure RDS Security Group
Write-Host "`n▶ STEP 9: Configure RDS Security Group" -ForegroundColor Magenta
Write-Host "======================================" -ForegroundColor Magenta

Write-Info "Getting RDS security group..."
$rdsInfo = aws rds describe-db-instances --db-instance-identifier $DBInstanceId --region $Region | ConvertFrom-Json
$RDS_SG = $rdsInfo.DBInstances[0].VpcSecurityGroups[0].VpcSecurityGroupId
Write-Info "RDS Security Group: $RDS_SG"

Write-Info "Adding EC2 security group to RDS inbound rules..."
try {
    aws ec2 authorize-security-group-ingress `
        --group-id $RDS_SG `
        --protocol tcp `
        --port 5432 `
        --source-group $SG_ID `
        --region $Region 2>&1 | Out-Null
    Write-Success "RDS security group updated"
} catch {
    Write-Warning-Custom "Rule might already exist, continuing..."
}

# Step 10: Create Environment File
Write-Host "`n▶ STEP 10: Create Environment Configuration" -ForegroundColor Magenta
Write-Host "==========================================" -ForegroundColor Magenta

# Generate JWT secret using PowerShell (avoid relying on openssl)
$jwtBytes = New-Object byte[] 48
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($jwtBytes)
$JWT_SECRET = [Convert]::ToBase64String($jwtBytes)

$envFile = @"
# ── Database (AWS RDS PostgreSQL) ────────────────────────────────────────────
DB_HOST=$DB_HOST
DB_PORT=$DB_PORT
DB_NAME=$DbName
DB_USERNAME=$DBUsername
DB_PASSWORD=$DBPassword

# ── Redis (Docker on EC2) ────────────────────────────────────────────────────
REDIS_ENABLED=true
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=
CACHE_TYPE=redis

# ── RabbitMQ (Docker on EC2) ─────────────────────────────────────────────────
RABBITMQ_ENABLED=true
RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_USER=guest
RABBITMQ_PASS=guest

# ── MongoDB (optional — leave blank to use in-memory fallback) ───────────────
MONGODB_URI=mongodb://localhost:27017/busymumkitchen_logs

# ── ECR Image ────────────────────────────────────────────────────────────────
ECR_IMAGE=$ECR_URI`:latest

# ── Server ───────────────────────────────────────────────────────────────────
SERVER_PORT=8080

# ── JWT ──────────────────────────────────────────────────────────────────────
JWT_SECRET=$JWT_SECRET

# ── AWS ──────────────────────────────────────────────────────────────────────
AWS_REGION=$Region
AWS_SNS_ENABLED=false
AWS_ACCESS_KEY=
AWS_SECRET_KEY=

# ── OTP ──────────────────────────────────────────────────────────────────────
OTP_DELIVERY_METHOD=DEV

# ── Email (Gmail SMTP — fill in to enable email OTP) ────────────────────────
MAIL_USERNAME=
MAIL_PASSWORD=

# ── Stripe (fill in your keys) ───────────────────────────────────────────────
STRIPE_SECRET_KEY=sk_test_your_key_here
STRIPE_WEBHOOK_SECRET=whsec_your_secret_here
"@

$envFile | Set-Content -Path "$PWD\.env.aws" -Encoding UTF8
Write-Success "Environment file created: $PWD\.env.aws"
Write-Warning-Custom "Please review and update sensitive values in .env.aws"

# Step 11: Generate Deployment Summary
Write-Host "`n▶ STEP 11: Deployment Summary" -ForegroundColor Magenta
Write-Host "==============================" -ForegroundColor Magenta

$summary = @"

╔════════════════════════════════════════════════════════════════════════╗
║                    DEPLOYMENT SUMMARY - SAVE THIS INFO                 ║
╚════════════════════════════════════════════════════════════════════════╝

✅ INFRASTRUCTURE CREATED/CONFIGURED:

  🔌 RDS PostgreSQL Database:
     Instance ID:  $DBInstanceId
     Endpoint:     $DB_HOST`:$DB_PORT
      Database:     $DBName
      Username:     $DBUsername
      Password:     $DBPassword [SAVE THIS!]

   [EC2 INSTANCE]
      Instance ID:  $INSTANCE_ID
      IP Address:   $EC2_IP
      Instance Type: $InstanceType
      Key Pair:     $keyName
      SSH Key File: $keyPath

   [SECURITY GROUPS]
      EC2 SG:      $SG_ID
      RDS SG:      $RDS_SG

   [ECR REPOSITORY]
      URI:         $ECR_URI
      Region:      $Region
      AWS Account: $AWS_ACCOUNT_ID

───────────────────────────────────────────────────────────────────────────

[NEXT STEPS]

   1. Update .env.aws with your secrets:
      - Stripe keys (if using Stripe)
      - AWS SNS credentials (if using SMS OTP)
      - JWT secret (already generated)

   2. Build and push Docker image to ECR:

      `$token = aws ecr get-login-password --region $Region
      `$token | docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$Region.amazonaws.com

      .\build-docker.ps1 -Push

   3. SSH into EC2 and initialize:

      ssh -i "$keyPath" ubuntu@${EC2_IP}

      Once connected:
      mkdir -p /opt/bmk/deploy
      cd /opt/bmk/deploy

   4. Copy docker-compose and .env files to EC2:

      scp -i "$keyPath" docker-compose.yml ubuntu@${EC2_IP}:/opt/bmk/deploy/
      scp -i "$keyPath" .env.aws ubuntu@${EC2_IP}:/opt/bmk/deploy/.env
      chmod 600 /opt/bmk/deploy/.env

   5. Start the application on EC2:

      docker-compose up -d

───────────────────────────────────────────────────────────────────────────

[SECURITY REMINDERS]

   • Store DB password and key file safely
   • Restrict SSH access to your IP in production
   • Use strong JWT and Stripe secrets
   • Regularly backup RDS database
   • Monitor EC2 CloudWatch logs

───────────────────────────────────────────────────────────────────────────

[HEALTH CHECK]

   • App:      http://${EC2_IP}:8080/api/v1/actuator/health
   • Docs:     http://${EC2_IP}:8080/swagger-ui.html
   • RabbitMQ: http://$EC2_IP:15672 (guest/guest)

───────────────────────────────────────────────────────────────────────────

[CONFIGURATION FILES SAVED]
   - .env.aws (environment variables)
   - bmk-key-fixed.pem (SSH private key)

═══════════════════════════════════════════════════════════════════════════
"@

Write-Host $summary
$summary | Out-File -FilePath "$PWD\DEPLOYMENT_SUMMARY.txt" -Encoding UTF8
Write-Success "Summary saved to: $PWD\DEPLOYMENT_SUMMARY.txt"

Write-Host "`n[COMPLETE] AWS Infrastructure deployment completed successfully!`n" -ForegroundColor Green

