# AWS deployment (Terraform + ECS Fargate)

This folder contains a minimal Terraform scaffold to deploy the backend as a Docker image on AWS ECS Fargate and an accompanying GitHub Actions workflow to provision infra, build and push the image, then force a deployment.

Prerequisites
- AWS credentials with permissions to manage ECR, ECS, IAM, CloudWatch and networking.
- `terraform` installed locally if you plan to run `terraform` from your machine.

Secrets (GitHub repository secrets)
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `AWS_REGION` (e.g. `us-east-1`)

How it works
1. The GitHub Actions workflow runs `terraform apply` in `backend/infra` to create an ECR repo, ECS cluster, task definition, service, log group and related IAM role.
2. The workflow builds the Docker image from `backend/Dockerfile`, pushes it to ECR tagged `:latest`.
3. The workflow forces a new ECS deployment to pull the new image.

Notes
- This is a minimal example. For production use you should:
  - Configure a remote Terraform state (S3 + DynamoDB)
  - Use immutable image tags and update task definition with new image tag instead of `:latest`
  - Add health checks, autoscaling, private subnets, and load balancer
