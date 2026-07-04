# Kubernetes provider — operates against the EKS cluster to create namespace + secrets
# that ArgoCD/Helm should NOT own (IaC-managed). State is local.
provider "kubernetes" {
  host                   = aws_eks_cluster.fds.endpoint
  cluster_ca_certificate = base64decode(aws_eks_cluster.fds.certificate_authority[0].data)
  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    command     = "aws"
    args = [
      "eks",
      "get-token",
      "--cluster-name",
      aws_eks_cluster.fds.name,
      "--region",
      var.region,
    ]
  }
}

# Namespace owned by Terraform; ArgoCD installs the Helm release into it (--create-namespace=false).
resource "kubernetes_namespace" "fds" {
  metadata {
    name = "fds"
  }
}

# Secret holding the Redis AUTH token. Owned by Terraform (not in git, not created by Helm).
# The Helm chart's secret.yaml is conditional (secret.create=false for uat) and only references this name.
resource "kubernetes_secret" "fds" {
  metadata {
    name      = "fds-secret"
    namespace = kubernetes_namespace.fds.metadata[0].name
  }

  data = {
    redis-password = local.redis_auth_token
    # AWS creds intentionally absent — pods use IRSA via the service account.
    aws-access-key-id     = ""
    aws-secret-access-key = ""
  }

  type = "Opaque"
}
