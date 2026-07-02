#!/bin/bash
set -e

echo "Creating SQS queues..."
awslocal sqs create-queue --queue-name fds-ticket-queue --region ap-southeast-1
echo "SQS queues created."
