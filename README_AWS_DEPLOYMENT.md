# 🚀 BusyMumKitchen AWS Deployment - Quick Reference

## File Overview

Created deployment files in your backend directory:

| File | Purpose |
|------|---------|
| `deploy-complete.ps1` | 🎯 **START HERE** - Interactive menu for full deployment |
| `deploy-aws.ps1` | Infrastructure setup (RDS, EC2, Security Groups) |
| `build-docker.ps1` | Build and push Docker image to ECR |
| `DEPLOYMENT_GUIDE.md` | Complete step-by-step guide |
| `.env.aws` | Environment variables (auto-generated) |
| `deploy/docker-compose.aws.yml` | Production docker-compose configuration |
| `deploy/ec2-init.sh` | EC2 initialization script (already exists) |

---

## ⚡ Quick Start (Copy & Paste)

### 1. Open PowerShell as Administrator

Right-click PowerShell → "Run as administrator"

### 2. Navigate to Backend

```powershell
cd D:\admin-flutter\busymumkitchen\backend
```

### 3. Run Deployment

```powershell
powershell -ExecutionPolicy Bypass -File deploy-complete.ps1
```

### 4. Choose Option

Select from menu:
- **1** = Full deployment (Infrastructure + Docker + Deploy)
- **2** = Infrastructure only
- **3** = Build & push Docker
- **4** = Deploy to EC2
- **5** = View deployment info

---

## 📋 What Gets Created

### AWS Infrastructure

✅ **RDS PostgreSQL**
- Instance: `bmk-postgres`
- Database: `busymumkitchen`
- User: `bmk_user`
- Automatic backups: 7 days

✅ **EC2 Instance**
- Type: `t3.medium` (2 vCPU, 4GB RAM)
- OS: Ubuntu 22.04
- Contains: Docker, Redis, RabbitMQ

✅ **Security Groups**
- HTTP (80), HTTPS (443), SSH (22), App (8080)
- RDS access from EC2

✅ **ECR Repository**
- Name: `bmk-backend`
- Region: `ap-south-1`

✅ **IAM Role**
- For EC2 to pull from ECR
- CloudWatch Logs access

---

## 🔑 Important Information

After running the scripts, save:

1. **Database Password** - In `.env.aws`
2. **SSH Key** - `bmk-key.pem` (keep safe!)
3. **Deployment Summary** - `DEPLOYMENT_SUMMARY.txt` (has IP, endpoints, credentials)
4. **Environment File** - `.env.aws` (with all configuration)

---

## 🐳 Docker Deployment

### Local Test (Before Pushing)

```powershell
# Build locally
docker build -t bmk-backend:test .

# Run locally
docker compose -f docker-compose.yml up
```

### Push to ECR

```powershell
# Build and push
& "$PWD\build-docker.ps1" -Push
```

---

## 🖥️ Access Points

After deployment, access via:

```
Application:    http://<EC2_IP>:8080
API Docs:       http://<EC2_IP>:8080/swagger-ui.html
Health Check:   http://<EC2_IP>:8080/api/v1/actuator/health
RabbitMQ:       http://<EC2_IP>:15672 (guest/guest)
```

---

## 🛠️ Common Commands

### SSH to EC2

```powershell
$EC2_IP = "your-ip-from-summary"
ssh -i "$PWD\bmk-key.pem" "ubuntu@$EC2_IP"
```

### View Application Logs

```powershell
ssh -i "$PWD\bmk-key.pem" "ubuntu@$EC2_IP" `
    "cd /opt/bmk/deploy && docker compose logs -f backend"
```

### Restart Services

```powershell
ssh -i "$PWD\bmk-key.pem" "ubuntu@$EC2_IP" `
    "cd /opt/bmk/deploy && docker compose restart"
```

### Redeploy Updated Image

```powershell
# 1. Build and push new image
& "$PWD\build-docker.ps1" -Push

# 2. Pull and restart on EC2
ssh -i "$PWD\bmk-key.pem" "ubuntu@$EC2_IP" `
    "cd /opt/bmk/deploy && docker compose pull && docker compose up -d"
```

### Check Service Status

```powershell
ssh -i "$PWD\bmk-key.pem" "ubuntu@$EC2_IP" `
    "cd /opt/bmk/deploy && docker compose ps"
```

---

## 💾 Environment Variables

Auto-generated in `.env.aws`, review before deployment:

```
DB_HOST=<RDS_ENDPOINT>
DB_PORT=5432
DB_NAME=busymumkitchen
DB_USERNAME=bmk_user
DB_PASSWORD=<GENERATED>

REDIS_HOST=redis
REDIS_PORT=6379

RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_USER=guest
RABBITMQ_PASS=guest

JWT_SECRET=<GENERATED>
SERVER_PORT=8080

# Add your own:
STRIPE_SECRET_KEY=sk_live_xxx
STRIPE_WEBHOOK_SECRET=whsec_xxx
```

---

## 🔒 Security Checklist

- [ ] Save `bmk-key.pem` in secure location
- [ ] Save `DEPLOYMENT_SUMMARY.txt` with credentials
- [ ] Update RDS password (different from generated one)
- [ ] Update JWT_SECRET every deployment
- [ ] Add Stripe keys from your dashboard
- [ ] Restrict SSH to your IP in production
- [ ] Set up SSL with Let's Encrypt (guide in DEPLOYMENT_GUIDE.md)
- [ ] Enable RDS backup
- [ ] Set up CloudWatch monitoring

---

## ⚠️ Common Issues

### AWS CLI Not Found

```powershell
# Install AWS CLI v2
# Visit: https://aws.amazon.com/cli/
# Or run in admin PowerShell:
Start-Process -FilePath 'msiexec.exe' `
    -ArgumentList '/i', 'C:\Temp\AWSCLIV2.msi', '/quiet', '/norestart' `
    -Wait -PassThru
```

### SSH Key Permission Denied

```powershell
# Run in admin PowerShell:
icacls "$PWD\bmk-key.pem" /inheritance:r
icacls "$PWD\bmk-key.pem" /grant:r "%USERNAME%:F"
```

### Docker Compose Pull Fails

```powershell
# Re-authenticate with ECR
$AWS_ACCOUNT_ID = $(aws sts get-caller-identity --query Account --output text)
ssh -i "$PWD\bmk-key.pem" "ubuntu@$EC2_IP" `
    "aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.ap-south-1.amazonaws.com"
```

### RDS Connection Refused

```powershell
# Check security group
$SG_ID = "sg-xxxxx"
aws ec2 describe-security-groups --group-ids $SG_ID --region ap-south-1

# Add EC2 security group to RDS rules:
# Inbound rule: Port 5432, from EC2 security group
```

---

## 📞 Need More Help?

1. **Deployment Guide**: Read `DEPLOYMENT_GUIDE.md` for detailed steps
2. **Original AWS Guide**: See `deploy/AWS_DEPLOYMENT_GUIDE.md`
3. **Container Logs**: Always check `docker compose logs`
4. **AWS CLI Help**: `aws ec2 help`

---

## 🎯 Deployment Workflow

```
1. Run deploy-complete.ps1
   ↓
2. Choose deployment phase
   ↓
3a. Infrastructure Setup
    ├─ Creates RDS PostgreSQL
    ├─ Launches EC2 instance
    ├─ Sets up security groups
    └─ Generates .env.aws
   ↓
3b. Build Docker Image
    ├─ Builds multi-arch image
    ├─ Tests locally
    └─ Pushes to ECR
   ↓
3c. Deploy to EC2
    ├─ Copies docker-compose.yml
    ├─ Copies .env file
    ├─ Starts services
    └─ Verifies health
   ↓
✅ Application Running!
   → Access at http://<EC2_IP>:8080
```

---

## 💰 Cost Breakdown (Monthly)

| Service | Tier | Cost |
|---------|------|------|
| EC2 t3.medium | pay-per-use | ~$15 |
| RDS db.t3.micro | free tier* | $0-12 |
| ECR | 20 images | ~$2 |
| Data transfer | minimal | ~$1 |
| **Total** | | ~$18-30 |

*Use free tier if within 12 months

---

## 📚 Scripts Reference

### deploy-complete.ps1
Interactive menu for entire deployment process
```powershell
& deploy-complete.ps1
```

### deploy-aws.ps1
Full infrastructure setup (run once)
```powershell
& deploy-aws.ps1
# Or with custom parameters:
& deploy-aws.ps1 -Region "ap-south-1" -DBPassword "mypassword"
```

### build-docker.ps1
Build Docker image locally or push to ECR
```powershell
# Build only (local):
& build-docker.ps1

# Build and push to ECR:
& build-docker.ps1 -Push

# Different region:
& build-docker.ps1 -Region "us-east-1" -Push
```

---

## ✨ What's Included

✅ Spring Boot 3.2.4 application  
✅ PostgreSQL 16 on RDS  
✅ Redis 7 caching  
✅ RabbitMQ 3.13 messaging  
✅ Docker containerization  
✅ ECR image registry  
✅ EC2 hosting  
✅ Automated health checks  
✅ CloudWatch logging  
✅ Flyway database migrations  

---

**Ready to deploy? Run:**
```powershell
powershell -ExecutionPolicy Bypass -File deploy-complete.ps1
```

---

Last Updated: April 2026

