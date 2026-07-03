# OIDC Provider for IRSA
data "tls_certificate" "eks" {
  url = aws_eks_cluster.fds.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "eks" {
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.eks.certificates[0].sha1_fingerprint]
  url             = aws_eks_cluster.fds.identity[0].oidc[0].issuer
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
          "${replace(aws_eks_cluster.fds.identity[0].oidc[0].issuer, "https://", "")}:sub" = "system:serviceaccount:fds:*"
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
          "sqs:GetQueueAttributes"
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
