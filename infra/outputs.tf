output "vpc_id" {
  value = aws_vpc.fds.id
}

output "private_subnet_ids" {
  value = aws_subnet.private[*].id
}

output "eks_cluster_name" {
  value = aws_eks_cluster.fds.name
}

output "eks_cluster_endpoint" {
  value = aws_eks_cluster.fds.endpoint
}

output "sqs_ticket_queue_url" {
  value = aws_sqs_queue.ticket.url
}

output "sqs_ticket_queue_arn" {
  value = aws_sqs_queue.ticket.arn
}

output "redis_endpoint" {
  value = aws_elasticache_serverless_cache.fds.endpoint[0].address
}

output "redis_port" {
  value = aws_elasticache_serverless_cache.fds.endpoint[0].port
}

output "ecr_sync_facade_url" {
  value = aws_ecr_repository.sync_facade.repository_url
}

output "ecr_rule_check_worker_url" {
  value = aws_ecr_repository.rule_check_worker.repository_url
}

output "oidc_provider_arn" {
  value = aws_iam_openid_connect_provider.eks.arn
}

output "pod_role_arn" {
  value = aws_iam_role.fds_pod.arn
}

output "fluent_bit_role_arn" {
  value = aws_iam_role.fds_fluent_bit.arn
}

output "otel_collector_role_arn" {
  value = aws_iam_role.fds_otel_collector.arn
}

output "github_actions_role_arn" {
  value = aws_iam_role.fds_github_actions.arn
}

output "redis_auth_token" {
  value     = local.redis_auth_token
  sensitive = true
}

output "cloudwatch_log_group" {
  value = aws_cloudwatch_log_group.fds.name
}
