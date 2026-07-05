# FDS — Getting Started

### 1. Provision AWS Infrastructure

```bash
cd infra
terraform init
terraform apply -auto-approve
```

Creates resources: VPC, EKS, SQS, ElastiCache, ECR, CloudWatch, IAM, EC2(for load test only).

### 2. Connect to EKS

```bash
aws eks update-kubeconfig --region ap-southeast-1 --name fds-uat
kubectl get nodes  
```

### 3. Deploy Fluent Bit (logs → CloudWatch)

```bash
helm repo add fluent https://fluent.github.io/helm-charts
helm upgrade --install fluent-bit fluent/fluent-bit -n fds \
  -f deploy/monitoring/fluent-bit-values-uat.yaml --wait --timeout 5m
```

### 4. Install ArgoCD

```bash
./scripts/bootstrap-argocd.sh
```


### 5. CI Build & Deploy

Push to GitHub → CI builds images to ECR → ArgoCD syncs the Helm chart → FDS services go live.

