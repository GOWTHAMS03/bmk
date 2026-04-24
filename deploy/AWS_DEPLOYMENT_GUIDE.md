# BusyMumKitchen – AWS Backend Deployment Guide

## Architecture

```
Internet
   │
   ▼
[EC2 t3.medium]  ─────────────────────────────────────────────
   ├── Nginx (port 80/443, SSL via Let's Encrypt)
   ├── Spring Boot app  (Docker, port 8080)
   ├── Redis            (Docker, internal)
   └── RabbitMQ         (Docker, internal)
       │
       ├── AWS RDS PostgreSQL  (managed)
       ├── MongoDB Atlas       (free tier)
       └── AWS SNS             (OTP via SMS)
```

**Cost estimate (ap-south-1):** ~$20–35/month
- EC2 t3.medium: ~$15/mo
- RDS db.t3.micro (PostgreSQL): ~$12/mo (or use free tier for 1 yr)
- MongoDB Atlas M0: Free
- AWS SNS: ~$0.50 per 100 SMS

---

## Prerequisites

On your **local machine**:
- [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html)
- Docker Desktop
- An AWS account with billing enabled
- A domain name (optional but recommended)

---

## Step 1 – Configure AWS CLI

```bash
aws configure
# AWS Access Key ID:     <your key>
# AWS Secret Access Key: <your secret>
# Default region:        ap-south-1
# Default output:        json
```

Verify:
```bash
aws sts get-caller-identity
```

---

## Step 2 – Create RDS PostgreSQL Database

```bash
# Create a DB subnet group first (uses default VPC subnets)
DB_SUBNETS=$(aws ec2 describe-subnets \
  --filters "Name=defaultForAz,Values=true" \
  --query "Subnets[*].SubnetId" --output text | tr '\t' ',')

aws rds create-db-subnet-group \
  --db-subnet-group-name bmk-db-subnet \
  --db-subnet-group-description "BMK DB Subnet Group" \
  --subnet-ids $(echo $DB_SUBNETS | tr ',' ' ')

# Create RDS instance
aws rds create-db-instance \
  --db-instance-identifier bmk-postgres \
  --db-instance-class db.t3.micro \
  --engine postgres \
  --engine-version 16.3 \
  --master-username bmk_user \
  --master-user-password "CHANGE_THIS_PASSWORD_NOW" \
  --db-name busymumkitchen \
  --allocated-storage 20 \
  --storage-type gp2 \
  --no-publicly-accessible \
  --db-subnet-group-name bmk-db-subnet \
  --backup-retention-period 7 \
  --deletion-protection \
  --region ap-south-1

# Wait until available (~5 minutes)
aws rds wait db-instance-available \
  --db-instance-identifier bmk-postgres

# Get the endpoint (put this in .env as DB_HOST)
aws rds describe-db-instances \
  --db-instance-identifier bmk-postgres \
  --query "DBInstances[0].Endpoint.Address" \
  --output text
```

---

## Step 3 – Create MongoDB Atlas (Free)

1. Go to [https://cloud.mongodb.com](https://cloud.mongodb.com) → Create account
2. Create a **free M0 cluster** (region: Mumbai / ap-south-1)
3. Create a DB user: Database Access → Add New Database User
4. Whitelist your EC2 IP: Network Access → Add IP Address
5. Connect → Drivers → copy the connection string
6. Replace `<password>` in the URI and save it as `MONGODB_URI`

---

## Step 4 – Launch EC2 Instance

```bash
# Get the latest Ubuntu 22.04 AMI for ap-south-1
AMI_ID=$(aws ec2 describe-images \
  --owners 099720109477 \
  --filters "Name=name,Values=ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*" \
            "Name=state,Values=available" \
  --query "sort_by(Images, &CreationDate)[-1].ImageId" \
  --output text \
  --region ap-south-1)
echo "AMI: $AMI_ID"

# Create a key pair (save the .pem file!)
aws ec2 create-key-pair \
  --key-name bmk-key \
  --query "KeyMaterial" \
  --output text \
  --region ap-south-1 > bmk-key.pem
chmod 400 bmk-key.pem

# Create security group
SG_ID=$(aws ec2 create-security-group \
  --group-name bmk-backend-sg \
  --description "BusyMumKitchen Backend Security Group" \
  --query "GroupId" --output text)

# Allow SSH (restrict to your IP in production!)
aws ec2 authorize-security-group-ingress \
  --group-id $SG_ID --protocol tcp --port 22 --cidr 0.0.0.0/0

# Allow HTTP + HTTPS
aws ec2 authorize-security-group-ingress \
  --group-id $SG_ID --protocol tcp --port 80 --cidr 0.0.0.0/0
aws ec2 authorize-security-group-ingress \
  --group-id $SG_ID --protocol tcp --port 443 --cidr 0.0.0.0/0

# Allow app port (for testing; remove once Nginx is configured)
aws ec2 authorize-security-group-ingress \
  --group-id $SG_ID --protocol tcp --port 8080 --cidr 0.0.0.0/0

echo "Security Group: $SG_ID"

# Launch EC2 (t3.medium = 2 vCPU / 4 GB)
INSTANCE_ID=$(aws ec2 run-instances \
  --image-id $AMI_ID \
  --instance-type t3.medium \
  --key-name bmk-key \
  --security-group-ids $SG_ID \
  --block-device-mappings "[{\"DeviceName\":\"/dev/sda1\",\"Ebs\":{\"VolumeSize\":30,\"VolumeType\":\"gp3\"}}]" \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=bmk-backend}]' \
  --iam-instance-profile Name=bmk-ec2-role \
  --region ap-south-1 \
  --query "Instances[0].InstanceId" --output text)

echo "Instance ID: $INSTANCE_ID"

# Wait for it to start
aws ec2 wait instance-running --instance-ids $INSTANCE_ID
EC2_IP=$(aws ec2 describe-instances \
  --instance-ids $INSTANCE_ID \
  --query "Reservations[0].Instances[0].PublicIpAddress" --output text)
echo "EC2 Public IP: $EC2_IP"
```

> **Note:** The `--iam-instance-profile` is required so EC2 can pull from ECR without storing AWS keys. Create it in Step 5.

---

## Step 5 – Create IAM Role for EC2 (ECR Pull Access)

```bash
# Create the trust policy
cat > /tmp/ec2-trust.json <<'EOF'
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": { "Service": "ec2.amazonaws.com" },
    "Action": "sts:AssumeRole"
  }]
}
EOF

# Create role
aws iam create-role \
  --role-name bmk-ec2-role \
  --assume-role-policy-document file:///tmp/ec2-trust.json

# Attach ECR read-only policy
aws iam attach-role-policy \
  --role-name bmk-ec2-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly

# Attach CloudWatch Logs (optional, for log streaming)
aws iam attach-role-policy \
  --role-name bmk-ec2-role \
  --policy-arn arn:aws:iam::aws:policy/CloudWatchLogsFullAccess

# Create instance profile
aws iam create-instance-profile --instance-profile-name bmk-ec2-role
aws iam add-role-to-instance-profile \
  --instance-profile-name bmk-ec2-role \
  --role-name bmk-ec2-role
```

> If you already launched the instance without the profile, attach it now:
> ```bash
> aws ec2 associate-iam-instance-profile \
>   --instance-id $INSTANCE_ID \
>   --iam-instance-profile Name=bmk-ec2-role
> ```

---

## Step 6 – Initialize the EC2 Instance

```bash
# SSH in
ssh -i bmk-key.pem ubuntu@$EC2_IP

# Inside EC2: run the bootstrap script
curl -fsSL https://raw.githubusercontent.com/<YOUR_REPO>/main/backend/deploy/ec2-init.sh | bash
# OR: scp and run locally
```

If you prefer to copy the script:
```bash
scp -i bmk-key.pem backend/deploy/ec2-init.sh ubuntu@$EC2_IP:/tmp/
ssh -i bmk-key.pem ubuntu@$EC2_IP "bash /tmp/ec2-init.sh"
```

---

## Step 7 – Configure Environment on EC2

```bash
# SSH into EC2
ssh -i bmk-key.pem ubuntu@$EC2_IP

# Create app directory
mkdir -p /opt/bmk/deploy
cd /opt/bmk/deploy

# Create .env from the template (fill in all values)
nano .env
# (copy from backend/deploy/.env.aws.template and fill in your values)

# Restrict permissions
chmod 600 .env
```

**Generate a secure JWT secret:**
```bash
openssl rand -base64 64
```

---

## Step 8 – Create ECR Repository & Build First Image

Run this on your **local machine** from the project root:

```bash
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
AWS_REGION=ap-south-1

# Create ECR repo
aws ecr create-repository \
  --repository-name bmk-backend \
  --image-scanning-configuration scanOnPush=true \
  --region $AWS_REGION

# Login to ECR
aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin \
  $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

# Build and push
cd backend
docker build --platform linux/amd64 \
  -t $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/bmk-backend:latest .
docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/bmk-backend:latest
```

---

## Step 9 – First Deployment on EC2

```bash
# SSH into EC2
ssh -i bmk-key.pem ubuntu@$EC2_IP
cd /opt/bmk/deploy

# Copy docker-compose.aws.yml from your machine first:
# (run this locally)
# scp -i bmk-key.pem backend/deploy/docker-compose.aws.yml ubuntu@$EC2_IP:/opt/bmk/deploy/docker-compose.yml

# On EC2: make sure .env has ECR_IMAGE set, then:
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
aws ecr get-login-password --region ap-south-1 | \
  docker login --username AWS --password-stdin \
  $AWS_ACCOUNT_ID.dkr.ecr.ap-south-1.amazonaws.com

# Start all services
docker compose up -d

# Check status
docker compose ps
docker compose logs backend --tail=100 -f
```

---

## Step 10 – Configure RDS Security Group

RDS must accept connections from EC2. Get the RDS security group and add an inbound rule:

```bash
# Get EC2 private IP or security group
EC2_SG_ID="<your EC2 security group ID>"

# Get RDS security group
RDS_SG_ID=$(aws rds describe-db-instances \
  --db-instance-identifier bmk-postgres \
  --query "DBInstances[0].VpcSecurityGroups[0].VpcSecurityGroupId" \
  --output text)

# Allow EC2 to connect to RDS on port 5432
aws ec2 authorize-security-group-ingress \
  --group-id $RDS_SG_ID \
  --protocol tcp \
  --port 5432 \
  --source-group $EC2_SG_ID
```

---

## Step 11 – SSL with Let's Encrypt (Nginx reverse proxy)

```bash
# On EC2:
sudo apt-get install -y nginx certbot python3-certbot-nginx

# Create Nginx config (replace api.busymumkitchen.com with your domain)
sudo tee /etc/nginx/sites-available/bmk <<'EOF'
server {
    listen 80;
    server_name api.busymumkitchen.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
        proxy_connect_timeout 10s;
        client_max_body_size 15M;
    }
}
EOF

sudo ln -s /etc/nginx/sites-available/bmk /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx

# Get SSL certificate
sudo certbot --nginx -d api.busymumkitchen.com --non-interactive \
  --agree-tos --email admin@busymumkitchen.com

# Auto-renew (runs twice daily via cron)
sudo systemctl enable certbot.timer
```

---

## Step 12 – Set Up CI/CD (GitHub Actions)

Add these secrets in GitHub → Settings → Secrets and variables → Actions:

| Secret | Value |
|--------|-------|
| `AWS_ACCESS_KEY_ID` | IAM user access key (create a dedicated CI/CD user) |
| `AWS_SECRET_ACCESS_KEY` | IAM user secret key |
| `EC2_HOST` | EC2 public IP or domain |
| `EC2_SSH_PRIVATE_KEY` | Contents of `bmk-key.pem` |

After adding secrets, any push to `main` that touches `backend/**` will automatically:
1. Run Maven tests
2. Build & push Docker image to ECR
3. Deploy to EC2 with health check

---

## Useful Commands

```bash
# View live logs
ssh -i bmk-key.pem ubuntu@$EC2_IP "cd /opt/bmk/deploy && docker compose logs backend -f"

# Restart backend
ssh -i bmk-key.pem ubuntu@$EC2_IP "cd /opt/bmk/deploy && docker compose restart backend"

# Check health
curl https://api.busymumkitchen.com/api/v1/actuator/health

# Manual deploy (local script)
export EC2_HOST="ubuntu@$EC2_IP"
export SSH_KEY="./bmk-key.pem"
cd backend/deploy && bash deploy.sh v1.0.0

# View all container resource usage
ssh -i bmk-key.pem ubuntu@$EC2_IP "docker stats --no-stream"

# Database backup
aws rds create-db-snapshot \
  --db-instance-identifier bmk-postgres \
  --db-snapshot-identifier bmk-backup-$(date +%Y%m%d)
```

---

## Environment Variable Checklist

Before going live, verify these are set in `.env`:

- [ ] `DB_HOST` – RDS endpoint
- [ ] `DB_PASSWORD` – strong password
- [ ] `MONGODB_URI` – Atlas connection string
- [ ] `JWT_SECRET` – 64+ char random string
- [ ] `AWS_ACCESS_KEY` + `AWS_SECRET_KEY` – for SNS
- [ ] `AWS_SNS_ENABLED=true`
- [ ] `OTP_DELIVERY_METHOD=SMS`
- [ ] `STRIPE_SECRET_KEY` – live key
- [ ] `STRIPE_WEBHOOK_SECRET` – from Stripe dashboard
- [ ] `ECR_IMAGE` – your ECR image URI

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| App won't connect to RDS | Check RDS security group allows EC2 IP/SG on port 5432 |
| `Flyway migration failed` | Check `DB_NAME` exists and `DB_USERNAME` has full privileges |
| `OTP not sending` | Verify `AWS_SNS_ENABLED=true` and IAM user has `AmazonSNSFullAccess` |
| Container keeps restarting | `docker compose logs backend` to see the error |
| 502 from Nginx | Backend not started yet – check health endpoint directly |
| `Connection refused` to Redis | Redis container not healthy – `docker compose ps` |
