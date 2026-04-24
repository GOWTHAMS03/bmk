output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.cluster.name
}

output "ecs_service_name" {
  value = aws_ecs_service.app_service.name
}

output "ecs_task_role_arn" {
  value = aws_iam_role.ecs_task_role.arn
}

output "rds_endpoint" {
  value = aws_db_instance.postgres.address
}

output "rds_port" {
  value = aws_db_instance.postgres.port
}

output "db_secret_arn" {
  value = aws_secretsmanager_secret.db_secret.arn
}

output "docdb_secret_arn" {
  value = aws_secretsmanager_secret.docdb_secret.arn
}

output "docdb_endpoint" {
  value = aws_docdb_cluster.docdb_cluster.endpoint
}

output "docdb_port" {
  value = aws_docdb_cluster.docdb_cluster.port
}

output "redis_primary_endpoint" {
  value = aws_elasticache_replication_group.redis.primary_endpoint_address
}

output "redis_port" {
  value = 6379
}

output "redis_secret_arn" {
  value = aws_secretsmanager_secret.redis_secret.arn
}

output "sqs_order_created_url" {
  value = aws_sqs_queue.order_created.id
}

output "sqs_order_updated_url" {
  value = aws_sqs_queue.order_updated.id
}

output "sqs_notification_url" {
  value = aws_sqs_queue.notification.id
}

output "sqs_analytics_url" {
  value = aws_sqs_queue.analytics.id
}
