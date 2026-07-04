# OIDC Provider for IRSA
data "tls_certificate" "eks" {
  url = aws_eks_cluster.fds.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "eks" {
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.eks.certificates[0].sha1_fingerprint]
  url             = aws_eks_cluster.fds.identity[0].oidc[0].issuer
}

locals {
  # EKS OIDC issuer without scheme, used in IRSA trust conditions.
  oidc_issuer = replace(aws_eks_cluster.fds.identity[0].oidc[0].issuer, "https://", "")
}

# Service Account role for Facade + Worker pods
resource "aws_iam_role" "fds_pod" {
  name = "fds-pod-${var.env}"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Federated = aws_iam_openid_connect_provider.eks.arn
      }
      Action = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${local.oidc_issuer}:sub" = "system:serviceaccount:fds:*"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "fds_pod" {
  name = "fds-pod-policy-${var.env}"
  role = aws_iam_role.fds_pod.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "sqs:SendMessage",
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueAttributes",
          "sqs:GetQueueUrl"
        ]
        Resource = [aws_sqs_queue.ticket.arn]
      },
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = ["*"]
      }
    ]
  })
}

# IRSA role for Fluent Bit DaemonSet (writes container stdout/stderr to CloudWatch Logs).
resource "aws_iam_role" "fds_fluent_bit" {
  name = "fds-fluent-bit-${var.env}"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.eks.arn }
      Action = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${local.oidc_issuer}:sub" = "system:serviceaccount:fds:fluent-bit"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "fds_fluent_bit" {
  name = "fds-fluent-bit-policy-${var.env}"
  role = aws_iam_role.fds_fluent_bit.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "logs:CreateLogStream",
        "logs:PutLogEvents",
        "logs:DescribeLogGroups",
        "logs:DescribeLogStreams"
      ]
      Resource = [
        aws_cloudwatch_log_group.fluent_bit.arn,
        "${aws_cloudwatch_log_group.fluent_bit.arn}:*"
      ]
    }]
  })
}

# IRSA role for OTel Collector (awsemf exporter writes EMF metrics to CloudWatch Logs).
resource "aws_iam_role" "fds_otel_collector" {
  name = "fds-otel-collector-${var.env}"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.eks.arn }
      Action = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${local.oidc_issuer}:sub" = "system:serviceaccount:fds:otel-collector"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "fds_otel_collector" {
  name = "fds-otel-collector-policy-${var.env}"
  role = aws_iam_role.fds_otel_collector.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents",
        "logs:DescribeLogGroups",
        "logs:DescribeLogStreams"
      ]
      Resource = [
        aws_cloudwatch_log_group.otel.arn,
        "${aws_cloudwatch_log_group.otel.arn}:*"
      ]
    }]
  })
}

# GitHub OIDC provider — lets GitHub Actions assume a role without stored credentials.
data "tls_certificate" "github" {
  url = "https://token.actions.githubusercontent.com"
}

resource "aws_iam_openid_connect_provider" "github" {
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.github.certificates[0].sha1_fingerprint]
  url             = "https://token.actions.githubusercontent.com"
}

# Role assumed by GitHub Actions on push to dev — pushes images to ECR only (least privilege).
resource "aws_iam_role" "fds_github_actions" {
  name = "fds-github-actions-${var.env}"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Action = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
        }
        StringLike = {
          "token.actions.githubusercontent.com:sub" = "repo:wushaobo/exercise-fda:*"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "fds_github_actions" {
  name = "fds-github-actions-policy-${var.env}"
  role = aws_iam_role.fds_github_actions.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "ecr:GetAuthorizationToken"
      ]
      Resource = ["*"]
      },
      {
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
          "ecr:PutImage",
          "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload"
        ]
        Resource = [
          aws_ecr_repository.sync_facade.arn,
          aws_ecr_repository.rule_check_worker.arn
        ]
      }
    ]
  })
}
