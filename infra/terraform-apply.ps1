# Helper script to init/plan/apply Terraform for backend infra
# Run from PowerShell. This script does NOT change AWS credentials.
param(
  [string]$S3Bucket = "",
  [bool]$RedisAuthEnabled = $false,
  [string]$SqsPrefix = "backend"
)

if (-not (Test-Path -Path .\main.tf)) {
  Write-Error "Run this from backend/infra directory"
  exit 1
}

if ($S3Bucket -eq "") {
  Write-Host "Warning: s3_bucket is empty; S3-related policy will reference an empty bucket."
}

Write-Host "Initializing Terraform..."
terraform init

Write-Host "Running terraform plan (this shows what will be created)."
terraform plan -out=tfplan \
  -var "s3_bucket=$S3Bucket" \
  -var "redis_auth_enabled=$RedisAuthEnabled" \
  -var "sqs_queue_prefix=$SqsPrefix"

if ($LASTEXITCODE -ne 0) { Write-Error "terraform plan failed"; exit $LASTEXITCODE }

Write-Host "Apply the plan? (y/n)"
$ans = Read-Host
if ($ans -ne 'y') { Write-Host "Aborting apply."; exit 0 }

Write-Host "Applying terraform plan..."
terraform apply tfplan

if ($LASTEXITCODE -eq 0) {
  Write-Host "Terraform apply completed. Review outputs with: terraform output"
} else {
  Write-Error "terraform apply failed"
}
