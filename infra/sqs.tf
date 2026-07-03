resource "aws_sqs_queue" "ticket_dlq" {
  name                      = "fds-ticket-dlq-${var.env}"
  message_retention_seconds = 1209600
  tags                      = { Name = "fds-ticket-dlq-${var.env}" }
}

resource "aws_sqs_queue" "ticket" {
  name                       = "fds-ticket-queue-${var.env}"
  delay_seconds              = var.sqs_delay_seconds
  max_message_size           = var.sqs_max_message_size
  message_retention_seconds  = var.sqs_retention_seconds
  receive_wait_time_seconds  = 10
  visibility_timeout_seconds = 60

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.ticket_dlq.arn
    maxReceiveCount     = 3
  })

  tags = { Name = "fds-ticket-queue-${var.env}" }
}
