# Run the entire infrastructure via Docker Compose

Write-Host "Starting PostgreSQL Database via Docker..."
docker compose -f docker/docker-compose.yml up -d

Write-Host "Container status:"
docker ps
