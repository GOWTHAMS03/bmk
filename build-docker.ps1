# Build and push Docker image to AWS ECR
# Run from the backend directory

param(
    [string]$Region = "ap-south-1",
    [string]$RepositoryName = "bmk-backend",
    [switch]$Push = $false
)

function Write-Success { Write-Host "[SUCCESS] $($args[0])" -ForegroundColor Green }
function Write-Error-Custom { Write-Host "[ERROR] $($args[0])" -ForegroundColor Red }
function Write-Info { Write-Host "[INFO] $($args[0])" -ForegroundColor Cyan }

Write-Host "`n[STEP 1] Docker Build & Push Script" -ForegroundColor Magenta
Write-Host "============================`n" -ForegroundColor Magenta

# Get AWS account ID
Write-Info "Getting AWS account information..."
try {
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
    $caller = aws sts get-caller-identity 2>&1 | ConvertFrom-Json
    $AWS_ACCOUNT_ID = $caller.Account
    Write-Success "AWS Account: $AWS_ACCOUNT_ID"
} catch {
    Write-Error-Custom "Failed to get AWS account ID. Make sure AWS CLI is configured."
    exit 1
}

$ECR_URI = "$AWS_ACCOUNT_ID.dkr.ecr.$Region.amazonaws.com/$RepositoryName"
Write-Info "ECR Repository: $ECR_URI"

# Build Docker image
Write-Host "`n[STEP 2] Building Docker Image" -ForegroundColor Magenta
Write-Host "======================`n" -ForegroundColor Magenta

Write-Info "Building image for Linux/AMD64 platform..."
$buildCmd = "docker build --platform linux/amd64 -t $($ECR_URI):latest -t $($ECR_URI):$(Get-Date -Format 'yyyyMMdd-HHmmss') ."

Write-Host "Running: $buildCmd`n" -ForegroundColor Gray
Invoke-Expression $buildCmd

if ($LASTEXITCODE -ne 0) {
    Write-Error-Custom "Docker build failed"
    exit 1
}

Write-Success "Docker image built successfully"

# Push to ECR
if ($Push) {
    Write-Host "`n[STEP 3] Pushing to ECR" -ForegroundColor Magenta
    Write-Host "================`n" -ForegroundColor Magenta

    $ECR_REGISTRY = "$AWS_ACCOUNT_ID.dkr.ecr.$Region.amazonaws.com"

    Write-Info "Logging into ECR ($ECR_REGISTRY)..."
    # Capture token into variable first — direct pipe can drop the registry arg in PS 5.1
    $ecrToken = aws ecr get-login-password --region $Region 2>&1
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrEmpty($ecrToken)) {
        Write-Error-Custom "Failed to retrieve ECR token. Check AWS credentials / permissions."
        exit 1
    }
    $ecrToken | docker login --username AWS --password-stdin $ECR_REGISTRY

    if ($LASTEXITCODE -ne 0) {
        Write-Error-Custom "ECR login failed"
        exit 1
    }
    Write-Success "Logged in to ECR"

    Write-Info "Pushing $($ECR_URI):latest ..."
    docker push "$($ECR_URI):latest"
    if ($LASTEXITCODE -ne 0) {
        Write-Error-Custom "Docker push failed"
        exit 1
    }

    Write-Success "Image pushed to ECR successfully!"
    Write-Info "Repository: $ECR_URI"
} else {
    Write-Host "Image built but not pushed. Use -Push flag to push to ECR`n" -ForegroundColor Yellow
    Write-Info "To push later, run:"
    Write-Info "  `$token = aws ecr get-login-password --region $Region"
    Write-Info "  `$token | docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$Region.amazonaws.com"
    Write-Info "  docker push ${ECR_URI}:latest"
}

Write-Host "`n[COMPLETE] Docker build completed!`n" -ForegroundColor Green
