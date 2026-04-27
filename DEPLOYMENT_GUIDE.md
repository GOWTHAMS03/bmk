# BusyMumKitchen AWS Deployment - Complete Implementation Guide

## 📋 Overview

This guide provides step-by-step instructions to deploy the BusyMumKitchen backend to AWS with:
- **PostgreSQL RDS** - Managed relational database
- **EC2 t3.medium** - Application server with Docker
- **Redis & RabbitMQ** - In containers on EC2
- **ECR** - Docker image registry
- **Nginx** - Reverse proxy with Let's Encrypt SSL

**Estimated Cost:** ~$20-35/month (ap-south-1 region)

---

## 🚀 Quick Start (5 Steps)

### Step 1: Open PowerShell as Administrator

```powershell
# Right-click PowerShell, select "Run as administrator"
```

### Step 2: Navigate to Backend Directory

```powershell
cd D:\admin-flutter\busymumkitchen\backend
```

### Step 3: Run Complete Deployment

```powershell
powershell -ExecutionPolicy Bypass -File deploy-complete.ps1
```

### Step 4: Follow the Interactive Menu

The script will prompt you to:
1. Choose deployment phase (Full, Infrastructure, Docker, etc.)
2. Configure AWS credentials (if not already configured)
3. Set up RDS PostgreSQL database
4. Launch EC2 instance
5. Build and push Docker image to ECR
6. Deploy to EC2

### Step 5: Access Your Application

```
Frontend: http://<EC2_IP>:8080
API Docs: http://<EC2_IP>:8080/swagger-ui.html
Health:   http://<EC2_IP>:8080/api/v1/actuator/health
```

---

## 📚 Detailed Steps

### Phase 1: Infrastructure Setup (15-20 minutes)

#### 1.1 Configure AWS CLI

If you haven't configured AWS CLI yet:

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
aws configure
```

You'll need:
- **AWS Access Key ID** - Get from AWS Console (IAM → Users → Your User → Security Credentials)
- **AWS Secret Access Key** - Displayed once during creation (save this!)
- **Default region** - `ap-south-1` (Mumbai)
- **Default output format** - `json`

Verify:
```powershell
aws sts get-caller-identity
```

#### 1.2 Run Infrastructure Setup

```powershell
& "$PWD\deploy-aws.ps1"
```

This script will:
1. ✅ Create IAM role for EC2 (ECR access)
2. ✅ Create RDS PostgreSQL database (5-10 minutes)
3. ✅ Create security groups
4. ✅ Create EC2 key pair
5. ✅ Launch EC2 instance (2-3 minutes)
6. ✅ Create ECR repository
7. ✅ Generate `.env.aws` configuration file

**Save the deployment summary!** It contains critical information:
- Database host and credentials
- EC2 IP address
- SSH key location

---

### Phase 2: Build Docker Image (5-10 minutes)

#### 2.1 Build Locally

```powershell
& "$PWD\build-docker.ps1"
```

This builds the Docker image but doesn't push it yet (good for testing).

#### 2.2 Build and Push to ECR

```powershell
& "$PWD\build-docker.ps1" -Push
```

This will:
1. Log into AWS ECR
2. Build multi-platform image (linux/amd64)
3. Push to ECR repository

Check ECR console to verify the image was pushed:
```powershell
aws ecr describe-images --repository-name bmk-backend --region ap-south-1
```

---

### Phase 3: Deploy to EC2 (10 minutes)

#### 3.1 Update Environment Configuration

Edit `.env.aws` with your secrets:

```powershell
notepad .env.aws
```

Key variables to review/update:
- `DB_HOST` - Auto-filled with RDS endpoint
- `DB_PASSWORD` - Your database password (saved during setup)
- `JWT_SECRET` - Already generated
- `STRIPE_SECRET_KEY` - Add your Stripe live key
- `STRIPE_WEBHOOK_SECRET` - From Stripe dashboard
- `MAIL_USERNAME` / `MAIL_PASSWORD` - For email OTP
- `AWS_ACCESS_KEY` / `AWS_SECRET_KEY` - For SNS (SMS OTP)

#### 3.2 Deploy to EC2

Manual deployment (if not using the complete script):

```powershell
$EC2_IP = "your-ec2-ip"
$KEY_PATH = "$PWD\bmk-key.pem"

# Copy docker-compose
scp -i $KEY_PATH "deploy\docker-compose.aws.yml" `
    "ubuntu@${EC2_IP}:/opt/bmk/deploy/docker-compose.yml"

# Copy environment file
scp -i $KEY_PATH ".env.aws" `
    "ubuntu@${EC2_IP}:/tmp/.env"

# SSH and setup
ssh -i $KEY_PATH "ubuntu@${EC2_IP}" @"
  sudo mv /tmp/.env /opt/bmk/deploy/.env
  sudo chmod 600 /opt/bmk/deploy/.env
  cd /opt/bmk/deploy
  docker compose up -d
"@
```

Or use the automated complete deployment:

```powershell
& "$PWD\deploy-complete.ps1"
```

#### 3.3 Verify Deployment

```powershell
# SSH into EC2
ssh -i "$PWD\bmk-key.pem" "ubuntu@$EC2_IP"

# Inside EC2:
cd /opt/bmk/deploy

# Check container status
docker compose ps

# View application logs
docker compose logs -f backend

# Test health endpoint
curl http://localhost:8080/api/v1/actuator/health
```

---

## 🔐 Post-Deployment: SSL & Production Setup

### Configure Nginx with Let's Encrypt

```bash
# SSH into EC2
ssh -i bmk-key.pem ubuntu@$EC2_IP

# Create Nginx config
sudo tee /etc/nginx/sites-available/bmk <<'EOF'
server {
    listen 80;
    server_name api.yourdomain.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
        client_max_body_size 15M;
    }
}
EOF

# Enable site
sudo ln -s /etc/nginx/sites-available/bmk /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx

# Get SSL certificate
sudo certbot --nginx -d api.yourdomain.com --non-interactive \
  --agree-tos --email admin@yourdomain.com

# Auto-renew
sudo systemctl enable certbot.timer
```

---

## 🛠️ Useful Commands

### View Logs
```powershell
ssh -i "$PWD\bmk-key.pem" "ubuntu@$EC2_IP" `
    "cd /opt/bmk/deploy && docker compose logs -f backend"
```

### Restart Services
```powershell
ssh -i "$PWD\bmk-key.pem" "ubuntu@$EC2_IP" `
    "cd /opt/bmk/deploy && docker compose restart backend"
```

### Update Application
```powershell
# Rebuild image
& "$PWD\build-docker.ps1" -Push

# Redeploy
ssh -i "$PWD\bmk-key.pem" "ubuntu@$EC2_IP" `
    "cd /opt/bmk/deploy && docker compose pull && docker compose up -d backend"
```

### Database Backup
```powershell
aws rds create-db-snapshot `
    --db-instance-identifier bmk-postgres `
    --db-snapshot-identifier "bmk-backup-$(Get-Date -Format 'yyyyMMdd-HHmmss')" `
    --region ap-south-1
```

### SSH Access
```powershell
ssh -i "$PWD\bmk-key.pem" ubuntu@$EC2_IP
```

### Check Docker Stats
```powershell
ssh -i "$PWD\bmk-key.pem" ubuntu@$EC2_IP "docker stats --no-stream"
```

---

## 🐛 Troubleshooting

### "Connection refused" to RDS

**Problem:** Application can't connect to PostgreSQL

**Solution:**
```powershell
# Check RDS security group allows EC2
$RDS_SG = "sg-xxxxx"
$EC2_SG = "sg-yyyyy"

aws ec2 authorize-security-group-ingress `
    --group-id $RDS_SG `
    --protocol tcp --port 5432 `
    --source-group $EC2_SG --region ap-south-1

# Verify from EC2
ssh -i bmk-key.pem ubuntu@$EC2_IP "psql -h <RDS_ENDPOINT> -U bmk_user -d busymumkitchen"
```

### Application keeps restarting

**Problem:** Container exits immediately

**Solution:**
```powershell
ssh -i bmk-key.pem ubuntu@$EC2_IP
cd /opt/bmk/deploy
docker compose logs backend --tail 50
```

Check for:
- Missing environment variables
- Database connection errors
- Port conflicts

### Docker Compose pull fails

**Problem:** "unauthorized: authentication required"

**Solution:**
```powershell
ssh -i bmk-key.pem ubuntu@$EC2_IP
aws ecr get-login-password --region ap-south-1 | \
    docker login --username AWS --password-stdin \
    $AWS_ACCOUNT_ID.dkr.ecr.ap-south-1.amazonaws.com
cd /opt/bmk/deploy && docker compose pull
```

### Out of memory / High CPU

**Monitor resources:**
```powershell
ssh -i bmk-key.pem ubuntu@$EC2_IP "docker stats"
```

**Increase instance size:**
```powershell
# Stop instance
aws ec2 stop-instances --instance-ids i-xxxxx --region ap-south-1
aws ec2 wait instance-stopped --instance-ids i-xxxxx --region ap-south-1

# Change to t3.large (double resources)
aws ec2 modify-instance-attribute --instance-id i-xxxxx \
    --instance-type t3.large --region ap-south-1

# Start instance
aws ec2 start-instances --instance-ids i-xxxxx --region ap-south-1
```

---

## 📊 Monitoring Setup

### CloudWatch Logs

View Docker logs in CloudWatch:
```powershell
aws logs describe-log-groups --region ap-south-1
aws logs tail /aws/ec2/bmk-backend --follow --region ap-south-1
```

### Health Endpoint

Monitor application health:
```powershell
while ($true) {
    $health = Invoke-WebRequest -Uri "http://$EC2_IP:8080/api/v1/actuator/health" -UseBasicParsing
    Write-Host "$(Get-Date): $($health.StatusCode)" 
    Start-Sleep -Seconds 30
}
```

### Database Metrics

View RDS metrics:
```powershell
aws cloudwatch get-metric-statistics `
    --namespace AWS/RDS `
    --metric-name DatabaseConnections `
    --dimensions Name=DBInstanceIdentifier,Value=bmk-postgres `
    --start-time $(Get-Date).AddHours(-1) `
    --end-time $(Get-Date) `
    --period 300 `
    --statistics Average `
    --region ap-south-1
```

---

## 💰 Cost Optimization

### Use RDS Free Tier
```powershell
# If you have AWS free tier, use db.t3.micro with 20GB storage
# This costs $0 for first 12 months
```

### Use MongoDB Atlas Free Tier
```
# Create M0 cluster (no cost, limited to 512MB)
# Perfect for development/testing
```

### Reserved Instances (if running 24/7)
```powershell
# 1-year reserved instance can save 35% on EC2
aws ec2 describe-reserved-instances-offerings `
    --instance-type t3.medium --region ap-south-1
```

### Auto-Shutdown During Off-Hours
```bash
# Add to EC2 user data for auto-shutdown
echo "0 23 * * * sudo shutdown -h" | crontab -
echo "0 8 * * * sudo shutdown -c" | crontab -
```

---

## 🔄 CI/CD Pipeline (GitHub Actions)

### Add GitHub Secrets

1. Go to GitHub Repository → Settings → Secrets and variables → Actions
2. Add these secrets:

| Secret | Value |
|--------|-------|
| `AWS_ACCESS_KEY_ID` | Your AWS access key |
| `AWS_SECRET_ACCESS_KEY` | Your AWS secret key |
| `EC2_HOST` | EC2 public IP or domain |
| `EC2_SSH_PRIVATE_KEY` | Contents of `bmk-key.pem` |
| `AWS_ACCOUNT_ID` | Your AWS account ID |

### Create GitHub Actions Workflow

Create `.github/workflows/deploy.yml`:

```yaml
name: Deploy to AWS

on:
  push:
    branches: [main]
    paths:
      - 'backend/**'
      - '.github/workflows/deploy.yml'

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up AWS CLI
        uses: aws-actions/configure-aws-credentials@v2
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ap-south-1
      
      - name: Build and push Docker image
        run: |
          cd backend
          aws ecr get-login-password | docker login -u AWS --password-stdin ${{secrets.AWS_ACCOUNT_ID}}.dkr.ecr.ap-south-1.amazonaws.com
          docker build -t ${{secrets.AWS_ACCOUNT_ID}}.dkr.ecr.ap-south-1.amazonaws.com/bmk-backend:latest .
          docker push ${{secrets.AWS_ACCOUNT_ID}}.dkr.ecr.ap-south-1.amazonaws.com/bmk-backend:latest
      
      - name: Deploy to EC2
        uses: appleboy/ssh-action@master
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ubuntu
          key: ${{ secrets.EC2_SSH_PRIVATE_KEY }}
          script: |
            cd /opt/bmk/deploy
            docker compose pull
            docker compose up -d
```

---

## 📝 Summary

You now have:
- ✅ **Infrastructure**: RDS PostgreSQL, EC2, security groups
- ✅ **Docker**: Multi-stage build, Alpine-based images
- ✅ **Deployment**: Automated scripts for easy updates
- ✅ **Monitoring**: Health checks and logging
- ✅ **Cost-effective**: ~$20-35/month

**Next Steps:**
1. Run the deployment script
2. Monitor the application
3. Set up SSL/Nginx
4. Configure CI/CD pipeline

---

## 📞 Need Help?

Check logs:
```powershell
# Local: Check if Docker build succeeds
docker build -t test .

# EC2: Check if containers are running
ssh -i bmk-key.pem ubuntu@$EC2_IP "cd /opt/bmk/deploy && docker compose ps"

# Logs: View application errors
ssh -i bmk-key.pem ubuntu@$EC2_IP "cd /opt/bmk/deploy && docker compose logs backend"
```

---

**Happy Deploying! 🚀**

