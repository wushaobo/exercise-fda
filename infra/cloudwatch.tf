resource "aws_cloudwatch_log_group" "fds" {
  name              = "/aws/eks/fds-${var.env}"
  retention_in_days = 30
  tags              = { Name = "fds-${var.env}" }
}

resource "aws_cloudwatch_log_group" "fluent_bit" {
  name              = "/aws/fluent-bit/fds-${var.env}"
  retention_in_days = 30
  tags              = { Name = "fds-fluent-bit-${var.env}" }
}
