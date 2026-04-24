terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.5"
    }
  }
}

provider "aws" {
  region = var.region
}

data "aws_vpc" "default" {
  default = true
}

data "aws_subnet_ids" "default" {
  vpc_id = data.aws_vpc.default.id
}

# DB master password
resource "random_password" "db_master_password" {
  length           = 16
  override_characters = "@#%&*()-_+="
  special          = true
}

# Subnet group for RDS using available subnets (for a production setup you should create dedicated private subnets)
resource "aws_db_subnet_group" "db_subnets" {
  name       = "${var.ecs_cluster_name}-db-subnet-group"
  subnet_ids = data.aws_subnet_ids.default.ids
  tags = { Name = "${var.ecs_cluster_name}-db-subnet-group" }
}

# Security group for RDS that only allows traffic from the ECS security group
resource "aws_security_group" "rds_sg" {
  name   = "rds-sg-${var.ecs_cluster_name}"
  vpc_id = data.aws_vpc.default.id

  ingress {
    from_port                = var.db_port
    to_port                  = var.db_port
    protocol                 = "tcp"
    security_groups          = [aws_security_group.ecs_sg.id]
    description              = "Allow DB access from ECS tasks"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# RDS Postgres instance (minimal example)
resource "aws_db_instance" "postgres" {
  identifier              = "${var.ecs_cluster_name}-postgres"
  engine                  = "postgres"
  engine_version          = var.db_engine_version
  instance_class          = var.db_instance_class
  allocated_storage       = var.db_allocated_storage
  name                    = var.db_name
  username                = var.db_username
  password                = random_password.db_master_password.result
  port                    = var.db_port
  db_subnet_group_name    = aws_db_subnet_group.db_subnets.name
  vpc_security_group_ids  = [aws_security_group.rds_sg.id]
  skip_final_snapshot     = true
  publicly_accessible     = false
  deletion_protection     = false
}

# Store DB password in Secrets Manager
resource "aws_secretsmanager_secret" "db_secret" {
  name = "${var.ecs_cluster_name}-db-password"
}

resource "aws_secretsmanager_secret_version" "db_secret_version" {
  secret_id     = aws_secretsmanager_secret.db_secret.id
  secret_string = random_password.db_master_password.result
}

# ------------------------ DocumentDB (Mongo-compatible) ------------------------
# Subnet group for DocumentDB
resource "aws_docdb_subnet_group" "docdb_subnets" {
  name       = "${var.ecs_cluster_name}-docdb-subnet-group"
  subnet_ids = data.aws_subnet_ids.default.ids
  tags = { Name = "${var.ecs_cluster_name}-docdb-subnet-group" }
}

# Security group for DocumentDB allowing access from ECS tasks
resource "aws_security_group" "docdb_sg" {
  name   = "docdb-sg-${var.ecs_cluster_name}"
  vpc_id = data.aws_vpc.default.id

  ingress {
    from_port       = 27017
    to_port         = 27017
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_sg.id]
    description     = "Allow Mongo/DocumentDB access from ECS tasks"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# Generate password for DocumentDB admin
resource "random_password" "docdb_master_password" {
  length           = 20
  override_characters = "@#%&*()-_+="
  special          = true
}

# DocumentDB cluster
resource "aws_docdb_cluster" "docdb_cluster" {
  cluster_identifier = "${var.ecs_cluster_name}-docdb"
  engine             = "docdb"
  engine_version     = var.docdb_engine_version
  master_username    = var.docdb_username
  master_password    = random_password.docdb_master_password.result
  skip_final_snapshot = true
  vpc_security_group_ids = [aws_security_group.docdb_sg.id]
  db_subnet_group_name   = aws_docdb_subnet_group.docdb_subnets.name
}

# DocumentDB instances
resource "aws_docdb_cluster_instance" "docdb_instances" {
  count              = var.docdb_instance_count
  identifier         = "${var.ecs_cluster_name}-docdb-instance-${count.index}"
  cluster_identifier = aws_docdb_cluster.docdb_cluster.id
  instance_class     = var.docdb_instance_class
  engine             = aws_docdb_cluster.docdb_cluster.engine
  engine_version     = aws_docdb_cluster.docdb_cluster.engine_version
}

# ------------------------ ElastiCache (Redis) ------------------------
resource "aws_elasticache_subnet_group" "redis_subnets" {
  name       = "${var.ecs_cluster_name}-redis-subnet-group"
  subnet_ids = data.aws_subnet_ids.default.ids
  tags = { Name = "${var.ecs_cluster_name}-redis-subnet-group" }
}

resource "aws_security_group" "redis_sg" {
  name   = "redis-sg-${var.ecs_cluster_name}"
  vpc_id = data.aws_vpc.default.id

  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_sg.id]
    description     = "Allow Redis access from ECS tasks"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# Generate auth token for Redis if enabled
resource "random_password" "redis_auth" {
  length           = 20
  override_characters = "@#%&*()-_+="
  special          = true
}

resource "aws_secretsmanager_secret" "redis_secret" {
  name = "${var.ecs_cluster_name}-redis-auth"
}

resource "aws_secretsmanager_secret_version" "redis_secret_version" {
  secret_id     = aws_secretsmanager_secret.redis_secret.id
  secret_string = var.redis_auth_enabled ? random_password.redis_auth.result : ""
}

resource "aws_elasticache_replication_group" "redis" {
  replication_group_id          = "${var.ecs_cluster_name}-redis"
  replication_group_description = "ElastiCache Redis for ${var.ecs_cluster_name}"
  engine                        = "redis"
  engine_version                = var.redis_engine_version
  node_type                     = var.redis_node_type
  number_cache_clusters         = 1
  parameter_group_name          = "default.redis6.x"
  subnet_group_name             = aws_elasticache_subnet_group.redis_subnets.name
  security_group_ids            = [aws_security_group.redis_sg.id]
  automatic_failover_enabled    = var.redis_replica_count > 0 ? true : false
  // When auth enabled, set auth_token; null/empty omitted
  auth_token                    = var.redis_auth_enabled ? random_password.redis_auth.result : null
}

# Store DocumentDB connection URI in Secrets Manager (so app can read it via task secret)
resource "aws_secretsmanager_secret" "docdb_secret" {
  name = "${var.ecs_cluster_name}-docdb-connection"
}

resource "aws_secretsmanager_secret_version" "docdb_secret_version" {
  secret_id = aws_secretsmanager_secret.docdb_secret.id
  secret_string = jsonencode({
    MONGODB_URI = "mongodb://${var.docdb_username}:${random_password.docdb_master_password.result}@${aws_docdb_cluster.docdb_cluster.endpoint}:${aws_docdb_cluster.docdb_cluster.port}/${var.docdb_name}?ssl=true&replicaSet=rs0&readPreference=secondaryPreferred&retryWrites=false"
  })
}

resource "aws_ecr_repository" "app" {
  name = var.ecr_repo_name
  image_tag_mutability = "MUTABLE"
  lifecycle_policy {
    policy = jsonencode({
      rules = [
        { rulePriority = 1, description = "Keep last 10", selection = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 10 }, action = { type = "expire" } }
      ]
    })
  }
}

resource "aws_ecs_cluster" "cluster" {
  name = var.ecs_cluster_name
}

resource "aws_cloudwatch_log_group" "ecs" {
  name              = "/ecs/${var.ecs_cluster_name}"
  retention_in_days = 14
}

resource "aws_iam_role" "ecs_task_execution" {
  name = "ecsTaskExecutionRole-${var.ecs_cluster_name}"
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Action = "sts:AssumeRole",
        Effect = "Allow",
        Principal = { Service = "ecs-tasks.amazonaws.com" }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_task_execution_policy" {
  role       = aws_iam_role.ecs_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# IAM role used by application containers (task role) to access AWS resources like S3
resource "aws_iam_role" "ecs_task_role" {
  name = "ecsTaskRole-${var.ecs_cluster_name}"
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Action = "sts:AssumeRole",
        Effect = "Allow",
        Principal = { Service = "ecs-tasks.amazonaws.com" }
      }
    ]
  })
}

# Least-privilege S3 policy for the application bucket
resource "aws_iam_policy" "s3_access_policy" {
  name        = "ecs-s3-access-${var.ecs_cluster_name}"
  description = "Allow ECS tasks to access application S3 bucket"
  policy      = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Sid = "AllowListBucket",
        Effect = "Allow",
        Action = ["s3:ListBucket"],
        Resource = ["arn:aws:s3:::${var.s3_bucket}"]
      },
      {
        Sid = "AllowObjectOps",
        Effect = "Allow",
        Action = ["s3:GetObject","s3:PutObject","s3:DeleteObject"],
        Resource = ["arn:aws:s3:::${var.s3_bucket}/*"]
      }
    ]
  })
}

# Allow ECS tasks to publish SMS via SNS
resource "aws_iam_policy" "sns_publish_policy" {
  name        = "ecs-sns-publish-${var.ecs_cluster_name}"
  description = "Allow ECS tasks to publish SMS via SNS"
  policy      = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Sid = "AllowSnsPublish",
        Effect = "Allow",
        Action = ["sns:Publish"],
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_task_role_attach_sns" {
  role       = aws_iam_role.ecs_task_role.name
  policy_arn = aws_iam_policy.sns_publish_policy.arn
}

resource "aws_iam_role_policy_attachment" "ecs_task_role_attach_s3" {
  role       = aws_iam_role.ecs_task_role.name
  policy_arn = aws_iam_policy.s3_access_policy.arn
}

resource "aws_iam_policy" "sqs_access_policy" {
  name        = "ecs-sqs-access-${var.ecs_cluster_name}"
  description = "Allow ECS tasks to use SQS queues for messaging"
  policy      = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Sid = "AllowSqsSendReceive",
        Effect = "Allow",
        Action = [
          "sqs:SendMessage",
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueUrl",
          "sqs:GetQueueAttributes"
        ],
        Resource = [
          aws_sqs_queue.order_created.arn,
          aws_sqs_queue.order_updated.arn,
          aws_sqs_queue.notification.arn,
          aws_sqs_queue.analytics.arn
        ]
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_task_role_attach_sqs" {
  role       = aws_iam_role.ecs_task_role.name
  policy_arn = aws_iam_policy.sqs_access_policy.arn
}

# ------------------------ SQS Queues ------------------------
resource "aws_sqs_queue" "order_created" {
  name = "${var.sqs_queue_prefix}-order-created"
}

resource "aws_sqs_queue" "order_updated" {
  name = "${var.sqs_queue_prefix}-order-updated"
}

resource "aws_sqs_queue" "notification" {
  name = "${var.sqs_queue_prefix}-notification"
}

resource "aws_sqs_queue" "analytics" {
  name = "${var.sqs_queue_prefix}-analytics"
}

resource "aws_security_group" "ecs_sg" {
  name   = "ecs-sg-${var.ecs_cluster_name}"
  vpc_id = data.aws_vpc.default.id
  ingress {
    from_port   = var.container_port
    to_port     = var.container_port
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_ecs_task_definition" "app" {
  family                   = "${var.ecs_cluster_name}-task"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = tostring(var.cpu)
  memory                   = tostring(var.memory)
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task_role.arn

  container_definitions = jsonencode([
    {
      name  = "app"
      image = "${aws_ecr_repository.app.repository_url}:latest"
      essential = true
      portMappings = [{ containerPort = var.container_port, protocol = "tcp" }]
      environment = [
        { name = "DB_HOST",  value = aws_db_instance.postgres.address },
        { name = "DB_PORT",  value = tostring(var.db_port) },
        { name = "DB_NAME",  value = var.db_name },
        { name = "DB_USERNAME", value = var.db_username }
        { name = "REDIS_HOST", value = aws_elasticache_replication_group.redis.primary_endpoint_address },
        { name = "REDIS_PORT", value = "6379" },
        { name = "SQS_ORDER_CREATED_URL", value = aws_sqs_queue.order_created.id },
        { name = "SQS_ORDER_UPDATED_URL", value = aws_sqs_queue.order_updated.id },
        { name = "SQS_NOTIFICATION_URL", value = aws_sqs_queue.notification.id },
        { name = "SQS_ANALYTICS_URL", value = aws_sqs_queue.analytics.id }
      ]
      secrets = [
        { name = "DB_PASSWORD", valueFrom = aws_secretsmanager_secret.db_secret.arn }
      ,
        { name = "MONGODB_URI", valueFrom = aws_secretsmanager_secret.docdb_secret.arn }
      ,
        { name = "REDIS_PASSWORD", valueFrom = aws_secretsmanager_secret.redis_secret.arn }
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.ecs.name
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = "ecs"
        }
      }
    }
  ])
}

resource "aws_ecs_service" "app_service" {
  name            = var.service_name
  cluster         = aws_ecs_cluster.cluster.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets         = data.aws_subnet_ids.default.ids
    security_groups = [aws_security_group.ecs_sg.id]
    assign_public_ip = true
  }

  depends_on = [aws_iam_role_policy_attachment.ecs_task_execution_policy]
}
