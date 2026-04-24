variable "region" {
  type    = string
  default = "us-east-1"
}

variable "ecr_repo_name" {
  type    = string
  default = "backend-app"
}

variable "ecs_cluster_name" {
  type    = string
  default = "backend-cluster"
}

variable "service_name" {
  type    = string
  default = "backend-service"
}

variable "container_port" {
  type    = number
  default = 8080
}

variable "cpu" {
  type    = number
  default = 256
}

variable "memory" {
  type    = number
  default = 512
}

variable "desired_count" {
  type    = number
  default = 1
}

variable "s3_bucket" {
  type    = string
  default = ""
  description = "S3 bucket name for storing uploaded images (required for S3 policy)."
}

variable "db_username" {
  type    = string
  default = "bmk_user"
}

variable "db_name" {
  type    = string
  default = "busymumkitchen"
}

variable "db_port" {
  type    = number
  default = 5432
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "db_allocated_storage" {
  type    = number
  default = 20
}

variable "db_engine_version" {
  type    = string
  default = "15"
}

variable "docdb_username" {
  type    = string
  default = "docdb_user"
}

variable "docdb_name" {
  type    = string
  default = "busymumkitchen_logs"
}

variable "docdb_instance_class" {
  type    = string
  default = "db.r6g.large"
}

variable "docdb_engine_version" {
  type    = string
  default = "4.0.0"
}

variable "docdb_instance_count" {
  type    = number
  default = 1
}

variable "redis_node_type" {
  type    = string
  default = "cache.t4g.micro"
}

variable "redis_replica_count" {
  type    = number
  default = 0
}

variable "redis_engine_version" {
  type    = string
  default = "6.x"
}

variable "redis_auth_enabled" {
  type    = bool
  default = false
}

variable "sqs_queue_prefix" {
  type    = string
  default = "${var.ecs_cluster_name}"
}
