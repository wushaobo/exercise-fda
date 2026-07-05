resource "aws_ecr_repository" "sync_facade" {
  name                 = "fds/sync-facade"
  image_tag_mutability = "IMMUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_repository" "rule_check_worker" {
  name                 = "fds/rule-check-worker"
  image_tag_mutability = "IMMUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "expire_old" {
  for_each   = toset([aws_ecr_repository.sync_facade.name, aws_ecr_repository.rule_check_worker.name])
  repository = each.key
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 20
      }
      action = { type = "expire" }
    }]
  })
}
