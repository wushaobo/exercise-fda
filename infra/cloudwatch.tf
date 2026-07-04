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

resource "aws_cloudwatch_log_group" "otel" {
  name              = "/aws/otel/fds-${var.env}"
  retention_in_days = 30
  tags              = { Name = "fds-otel-${var.env}" }
}

# Dashboard for perf test observability. OTel awsemf → EMF → metrics land in
# namespace "FDS" (configured in otel-collector values). Live URL:
# https://ap-southeast-1.console.aws.amazon.com/cloudwatch/home?region=ap-southeast-1#dashboards:name=fds-uat
resource "aws_cloudwatch_dashboard" "fds" {
  dashboard_name = "fds-${var.env}"

  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric"
        x    = 0, y = 0, width = 12, height = 6
        properties = {
          title   = "sync-facade JVM Memory (OTel)"
          region  = var.region
          view    = "timeSeries"
          stacked = false
          metrics = [
            ["FDS", "jvm.memory.used"]
          ]
        }
      },
      {
        type = "metric"
        x    = 12, y = 0, width = 12, height = 6
        properties = {
          title   = "sync-facade JVM GC Duration (OTel)"
          region  = var.region
          view    = "timeSeries"
          stacked = false
          metrics = [
            ["FDS", "jvm.gc.duration"]
          ]
        }
      },
      {
        type = "metric"
        x    = 0, y = 6, width = 12, height = 6
        properties = {
          title   = "SQS Ticket Queue — Received vs Visible"
          region  = var.region
          view    = "timeSeries"
          stacked = false
          metrics = [
            ["AWS/SQS", "NumberOfMessagesReceived", "QueueName", "fds-ticket-queue-uat"],
            [".", "ApproximateNumberOfMessagesVisible", "QueueName", "fds-ticket-queue-uat"]
          ]
        }
      },
      {
        type = "metric"
        x    = 12, y = 6, width = 12, height = 6
        properties = {
          title   = "EKS Node CPUUtilization (isolation check: test-pool spikes, fds-pool unaffected)"
          region  = var.region
          view    = "timeSeries"
          stacked = false
          metrics = [
            ["AWS/EC2", "CPUUtilization"]
          ]
        }
      }
    ]
  })
}
