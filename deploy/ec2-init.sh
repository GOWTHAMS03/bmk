#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# BusyMumKitchen – EC2 Instance Bootstrap Script
#
# USAGE: Paste this into EC2 → "User Data" when launching, OR run manually
#        on a fresh Ubuntu 22.04 / Amazon Linux 2023 instance.
#
# What this script does:
#   1. Updates the OS
#   2. Installs Docker + Docker Compose
#   3. Installs AWS CLI v2
#   4. Configures Docker to start on boot
#   5. Creates the app directory structure
#   6. Sets up log rotation
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

echo "===== [1/6] Updating system ====="
apt-get update -y
apt-get upgrade -y

echo "===== [2/6] Installing Docker ====="
apt-get install -y ca-certificates curl gnupg lsb-release unzip git

# Docker official GPG key
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
  gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

# Docker apt repo
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | \
  tee /etc/apt/sources.list.d/docker.list > /dev/null

apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Add ubuntu user to docker group
usermod -aG docker ubuntu

echo "===== [3/6] Installing AWS CLI v2 ====="
curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
unzip -q /tmp/awscliv2.zip -d /tmp/
/tmp/aws/install
rm -rf /tmp/awscliv2.zip /tmp/aws/

echo "===== [4/6] Enabling Docker on boot ====="
systemctl enable docker
systemctl start docker

echo "===== [5/6] Creating app directory ====="
mkdir -p /opt/bmk/deploy
chown -R ubuntu:ubuntu /opt/bmk

echo "===== [6/6] Configuring log rotation ====="
cat > /etc/logrotate.d/docker-containers <<'EOF'
/var/lib/docker/containers/*/*.log {
    rotate 7
    daily
    compress
    missingok
    delaycompress
    copytruncate
}
EOF

echo ""
echo "✅ EC2 Bootstrap complete."
echo "   Next: upload deploy files and run deploy.sh"
echo "   Docker version: $(docker --version)"
echo "   AWS CLI version: $(aws --version)"
