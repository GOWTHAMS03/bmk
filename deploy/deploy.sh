#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# BusyMumKitchen – Build, Push to ECR, and Deploy to EC2
#
# PREREQUISITES (run once on your local machine):
#   aws configure   # set Access Key, Secret Key, region=ap-south-1
#
# USAGE:
#   ./deploy.sh [tag]        # tag defaults to "latest"
#   ./deploy.sh v1.2.3       # deploy with version tag
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

# ── Config ────────────────────────────────────────────────────────────────────
AWS_REGION="ap-south-1"
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_REPO="bmk-backend"
TAG="${1:-latest}"
IMAGE_URI="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPO}:${TAG}"
EC2_HOST="${EC2_HOST:?Set EC2_HOST environment variable, e.g. export EC2_HOST=ubuntu@1.2.3.4}"
SSH_KEY="${SSH_KEY:?Set SSH_KEY environment variable, e.g. export SSH_KEY=~/.ssh/bmk-key.pem}"
# ─────────────────────────────────────────────────────────────────────────────

echo "=== BusyMumKitchen Deploy ==="
echo "  Region:    $AWS_REGION"
echo "  Account:   $AWS_ACCOUNT_ID"
echo "  Image:     $IMAGE_URI"
echo "  EC2 Host:  $EC2_HOST"
echo ""

# ── Step 1: Create ECR repo if it doesn't exist ───────────────────────────────
echo "[1/5] Ensuring ECR repo exists..."
aws ecr describe-repositories --repository-names "$ECR_REPO" --region "$AWS_REGION" \
  > /dev/null 2>&1 || \
  aws ecr create-repository \
    --repository-name "$ECR_REPO" \
    --region "$AWS_REGION" \
    --image-scanning-configuration scanOnPush=true \
    --query "repository.repositoryUri" --output text

# ── Step 2: Authenticate Docker to ECR ───────────────────────────────────────
echo "[2/5] Logging Docker into ECR..."
aws ecr get-login-password --region "$AWS_REGION" | \
  docker login --username AWS --password-stdin \
  "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

# ── Step 3: Build the Docker image ────────────────────────────────────────────
echo "[3/5] Building Docker image..."
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(dirname "$SCRIPT_DIR")"

docker build \
  --platform linux/amd64 \
  --tag "$IMAGE_URI" \
  --tag "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPO}:latest" \
  "$BACKEND_DIR"

# ── Step 4: Push to ECR ───────────────────────────────────────────────────────
echo "[4/5] Pushing image to ECR..."
docker push "$IMAGE_URI"
if [ "$TAG" != "latest" ]; then
  docker push "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPO}:latest"
fi

# ── Step 5: Deploy on EC2 ─────────────────────────────────────────────────────
echo "[5/5] Deploying on EC2..."
ssh -i "$SSH_KEY" -o StrictHostKeyChecking=no "$EC2_HOST" bash <<REMOTE
set -e

# Upload latest deploy files
echo "  → Syncing deploy files"
REMOTE
# (we copy files via scp below, then re-ssh for the rest)
scp -i "$SSH_KEY" -o StrictHostKeyChecking=no \
  "$SCRIPT_DIR/docker-compose.aws.yml" \
  "$EC2_HOST:/opt/bmk/deploy/docker-compose.yml"

ssh -i "$SSH_KEY" -o StrictHostKeyChecking=no "$EC2_HOST" bash <<REMOTE
set -e
cd /opt/bmk/deploy

# ECR login on the server
echo "  → Logging into ECR on server"
aws ecr get-login-password --region ${AWS_REGION} | \
  docker login --username AWS --password-stdin \
  ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com

# Update image reference in .env
sed -i "s|^ECR_IMAGE=.*|ECR_IMAGE=${IMAGE_URI}|" .env

# Pull new image and restart backend only (zero-downtime)
echo "  → Pulling new image"
docker compose pull backend

echo "  → Restarting backend"
docker compose up -d --no-deps --force-recreate backend

echo "  → Cleaning up old images"
docker image prune -f

echo "  → Verifying health"
sleep 10
docker compose ps
REMOTE

echo ""
echo "✅ Deployment complete!"
echo "   Image: $IMAGE_URI"
echo "   Check: curl http://<EC2_IP>:8080/api/v1/actuator/health"
