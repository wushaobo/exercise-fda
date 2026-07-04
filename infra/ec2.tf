# Independent EC2 in the same VPC as EKS to run ghz against sync-facade's
# internal NLB. Low latency (same VPC), and CPU is fully isolated from EKS
# worker nodes — ghz load never starves sync-facade. Destroyed before sleep.
# tls_private_key generates an ephemeral keypair so we don't need a pre-shared
# public key; private key is exported (sensitive) for run.sh to ssh with.

resource "tls_private_key" "ghz_runner" {
  algorithm = "ED25519"
}

resource "aws_key_pair" "ghz_runner" {
  key_name   = "ghz-runner-${var.env}"
  public_key = tls_private_key.ghz_runner.public_key_openssh
}

resource "aws_security_group" "ghz_runner" {
  name        = "ghz-runner-${var.env}"
  description = "ghz runner: SSH in, all egress (NLB 9090 + ghcr pull)"
  vpc_id      = aws_vpc.fds.id

  ingress {
    description = "SSH from anywhere (MVP; destroy before sleep)"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "ghz-runner-${var.env}" }
}

data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]
  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }
}

resource "aws_instance" "ghz_runner" {
  ami                    = data.aws_ami.al2023.id
  instance_type          = "t3.medium"
  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.ghz_runner.id]
  key_name               = aws_key_pair.ghz_runner.key_name

  user_data = <<-EOF
              #!/bin/bash
              dnf install -y docker
              systemctl enable --now docker
              usermod -a -G docker ec2-user
              EOF

  tags = { Name = "ghz-runner-${var.env}" }
}
