variable "region" {
  description = "AWS region"
  type        = string
  default     = "ap-southeast-1"
}

variable "env" {
  description = "Environment name"
  type        = string
  default     = "uat"
}

variable "cluster_name" {
  description = "EKS cluster name"
  type        = string
  default     = "fds-uat"
}

variable "node_instance_type" {
  description = "EC2 instance type for EKS node groups"
  type        = string
  default     = "t3.medium"
}

variable "fds_node_desired" {
  description = "Desired nodes in fds pool"
  type        = number
  default     = 2
}

variable "fds_node_max" {
  description = "Max nodes in fds pool"
  type        = number
  default     = 4
}

variable "monitor_node_desired" {
  description = "Desired nodes in monitor pool"
  type        = number
  default     = 1
}

variable "az_count" {
  description = "Number of AZs"
  type        = number
  default     = 2
}

variable "redis_auth_token" {
  description = "ElastiCache Redis AUTH token (leave empty to auto-generate)"
  type        = string
  default     = ""
  sensitive   = true
}

variable "sqs_delay_seconds" {
  type    = number
  default = 0
}

variable "sqs_max_message_size" {
  type    = number
  default = 262144
}

variable "sqs_retention_seconds" {
  type    = number
  default = 345600
}
