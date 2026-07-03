resource "aws_security_group" "redis" {
  name        = "fds-redis-${var.env}"
  description = "ElastiCache Redis for FDS"
  vpc_id      = aws_vpc.fds.id

  ingress {
    from_port   = 6379
    to_port     = 6379
    protocol    = "tcp"
    cidr_blocks = aws_subnet.private[*].cidr_block
  }
}

resource "aws_elasticache_subnet_group" "fds" {
  name       = "fds-redis-${var.env}"
  subnet_ids = aws_subnet.private[*].id
}

resource "random_password" "redis_auth" {
  count   = var.redis_auth_token == "" ? 1 : 0
  length  = 32
  special = false
}

resource "aws_elasticache_serverless_cache" "fds" {
  engine = "redis"
  name   = "fds-redis-${var.env}"

  cache_usage_limits {
    data_storage {
      maximum = 10
      minimum = 1
      unit    = "GB"
    }
    ecpu_per_second {
      maximum = 100000
      minimum = 1000
    }
  }

  daily_snapshot_time      = "03:00"
  description              = "FDS result cache + Pub/Sub + denylist"
  major_engine_version     = "7"
  security_group_ids       = [aws_security_group.redis.id]
  snapshot_retention_limit = 7
  subnet_ids               = aws_subnet.private[*].id
}
