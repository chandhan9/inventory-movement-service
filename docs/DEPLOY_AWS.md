# Deploying to AWS (EC2 + Docker Compose)

This is the simplest way to get the full stack (service + PostgreSQL + Kafka) running on AWS.

> Check current AWS pricing before you start, and **stop or terminate the instance when you
> are not demoing it** to avoid charges. Kafka + PostgreSQL + the JVM need about 2 GB of RAM,
> so a `t3.small` is the smallest size that runs the full stack comfortably.

## 1. Launch an EC2 instance

1. AWS Console → EC2 → **Launch instance**
2. AMI: **Amazon Linux 2023**; instance type: **t3.small**
3. Create or choose a key pair
4. Security group inbound rules:
   - SSH (22) from **My IP**
   - Custom TCP **8080** from **My IP** (or anywhere, for a short public demo)

## 2. Install Docker and Git

```bash
ssh -i your-key.pem ec2-user@<public-ip>

sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
exit   # log out and back in so the group change applies

# Docker Compose plugin
ssh -i your-key.pem ec2-user@<public-ip>
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
docker compose version
```

## 3. Deploy

```bash
git clone https://github.com/<your-username>/inventory-movement-service.git
cd inventory-movement-service
docker compose up -d --build
docker compose logs -f app      # wait for "Started InventoryApplication"
```

## 4. Verify

```bash
curl http://<public-ip>:8080/actuator/health
```

Open `http://<public-ip>:8080/swagger-ui.html` in your browser and try the API.

## 5. Redeploy after changes

```bash
git pull
docker compose up -d --build app
```

## Next steps (optional, good to discuss in interviews)

- Push the image to **Amazon ECR** and run it on **ECS Fargate** or **EKS** (the `k8s/` manifests are a starting point)
- Replace the PostgreSQL container with **Amazon RDS**, and Kafka with **Amazon MSK**
  (or swap the publisher for **SQS**; only one class changes, thanks to the `InventoryEventPublisher` interface)
- Send the Prometheus metrics to **Amazon Managed Prometheus / Grafana**, or use **CloudWatch** alarms
