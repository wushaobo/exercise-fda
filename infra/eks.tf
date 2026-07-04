resource "aws_iam_role" "eks_cluster" {
  name = "fds-eks-cluster-${var.env}"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "eks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "eks_cluster" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
  role       = aws_iam_role.eks_cluster.name
}

resource "aws_eks_cluster" "fds" {
  name     = var.cluster_name
  role_arn = aws_iam_role.eks_cluster.arn
  version  = "1.33"

  vpc_config {
    subnet_ids              = aws_subnet.private[*].id
    endpoint_private_access = true
    endpoint_public_access  = true
  }

  depends_on = [aws_iam_role_policy_attachment.eks_cluster]
}

resource "aws_iam_role" "eks_node" {
  name = "fds-eks-node-${var.env}"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "eks_worker" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
  role       = aws_iam_role.eks_node.name
}

resource "aws_iam_role_policy_attachment" "eks_cni" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
  role       = aws_iam_role.eks_node.name
}

resource "aws_iam_role_policy_attachment" "eks_ecr" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
  role       = aws_iam_role.eks_node.name
}

resource "aws_eks_node_group" "fds_pool" {
  cluster_name    = aws_eks_cluster.fds.name
  node_group_name = "fds-pool"
  node_role_arn   = aws_iam_role.eks_node.arn
  subnet_ids      = aws_subnet.private[*].id
  instance_types  = [var.node_instance_type]

  scaling_config {
    desired_size = var.fds_node_desired
    max_size     = var.fds_node_max
    min_size     = 1
  }

  labels = { pool = "fds" }

  depends_on = [aws_iam_role_policy_attachment.eks_worker, aws_iam_role_policy_attachment.eks_cni, aws_iam_role_policy_attachment.eks_ecr]
}

resource "aws_eks_node_group" "monitor_pool" {
  cluster_name    = aws_eks_cluster.fds.name
  node_group_name = "monitor-pool"
  node_role_arn   = aws_iam_role.eks_node.arn
  subnet_ids      = aws_subnet.private[*].id
  instance_types  = ["t3.medium"]

  scaling_config {
    desired_size = var.monitor_node_desired
    max_size     = 2
    min_size     = 1
  }

  labels = { pool = "monitor" }

  depends_on = [aws_iam_role_policy_attachment.eks_worker, aws_iam_role_policy_attachment.eks_cni, aws_iam_role_policy_attachment.eks_ecr]
}
