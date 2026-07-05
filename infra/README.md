# Terraform Infrastructure

AWS infrastructure for FDS: VPC, EKS, SQS, ElastiCache, ECR, IAM, CloudWatch.

All commands assume the working directory is `fds/`. Use `-chdir=infra` to operate
inside this directory without `cd` .

### Common Commands

```bash
# validate and preview
terraform -chdir=infra validate                          # syntax check
terraform -chdir=infra plan                              # preview changes (no apply)
terraform -chdir=infra apply                             # create / update all infrastructure

# targeted operations (one-off fixes only — see Gotchas below)
terraform -chdir=infra apply -target=aws_cloudwatch_dashboard.fds      # apply a single resource
terraform -chdir=infra destroy -target=aws_instance.ghz_runner         # destroy a single resource (e.g. EC2 overnight)
terraform -chdir=infra apply -replace=aws_iam_role.fds_pod             # force-recreate a resource (state drift)

# teardown
terraform -chdir=infra destroy                          # destroy everything (full cluster teardown)

# read outputs
terraform -chdir=infra output                           # list all outputs (JSON, human-readable)
terraform -chdir=infra output -raw ghz_runner_public_ip             # single raw value (pipe into scripts)
terraform -chdir=infra output -raw ghz_runner_private_key > /tmp/ghzkey && chmod 600 /tmp/ghzkey   # sensitive output to file

# inspect state
terraform -chdir=infra state list                       # list resources in state (confirm teardown emptied it)
```

### Gotchas

- **`-target` is for one-off fixes, not routine use.** Terraform officially warns it
  leaves state drift. Use it to patch a single resource (dashboard, one IAM role, one
  EC2) or tear down a single resource; use full `apply` for everyday changes.
- **ECR `force_delete`.** Repos with images fail `destroy` (`RepositoryNotEmptyException`).
  `infra/ecr.tf` sets `force_delete = true` so `destroy` clears them. Note: `destroy`
  reads `force_delete` from **state**, not config — if you flip it in config, run
  `apply` once before `destroy`, or `aws ecr delete-repository --force` then `destroy`.
- **`destroy` refreshes state first.** Resources deleted out-of-band (e.g. via AWS CLI)
  are detected as gone and removed from state automatically — safe to follow CLI deletes
  with `terraform destroy` to clean state.
- **`output -raw` for scripts, plain `output` for humans.** `-raw` prints the bare string
  (no quotes) so it pipes cleanly; bare `output` prints JSON.

