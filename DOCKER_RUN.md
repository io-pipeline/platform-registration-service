# Running platform-registration-service in Docker

## Prerequisites

The service requires the shared infrastructure stack to be running. This includes:
- MySQL (pipeline-mysql:3306)
- Consul (pipeline-consul:8500)
- Kafka (pipeline-kafka:9092)
- Apicurio Registry (pipeline-apicurio-registry:8080)

## Start Shared Infrastructure

First, start the devservices infrastructure stack:

```bash
# Extract and run the shared compose file from the monorepo or devservices library
cd /path/to/pipeline-engine-refactor
docker compose -f src/test/resources/compose-devservices.yml up -d

# Verify infrastructure is running
docker ps | grep pipeline
```

## Run the Service Container

### Option 1: Using docker run

```bash
docker run -d \
  --name platform-registration-service \
  --network pipeline-shared-devservices_pipeline-test-network \
  -p 38101:8080 \
  -e QUARKUS_HTTP_PORT="8080" \
  -e QUARKUS_DATASOURCE_JDBC_URL="jdbc:mysql://pipeline-mysql:3306/pipeline" \
  -e QUARKUS_DATASOURCE_USERNAME="pipeline" \
  -e QUARKUS_DATASOURCE_PASSWORD="password" \
  -e QUARKUS_DATASOURCE_REACTIVE_URL="mysql://pipeline-mysql:3306/pipeline" \
  -e PIPELINE_CONSUL_HOST="pipeline-consul" \
  -e PIPELINE_CONSUL_PORT="8500" \
  -e KAFKA_BOOTSTRAP_SERVERS="pipeline-kafka:9092" \
  -e MP_MESSAGING_CONNECTOR_SMALLRYE_KAFKA_APICURIO_REGISTRY_URL="http://pipeline-apicurio-registry:8080/apis/registry/v3" \
  -e QUARKUS_LOG_LEVEL="INFO" \
  -e QUARKUS_LOG_CATEGORY_IO_PIPELINE_LEVEL="DEBUG" \
  ghcr.io/ai-pipestream/platform-registration-service:latest
```

### Option 2: Using docker-compose

```bash
# Use the provided docker-compose.test.yml
docker compose -f docker-compose.test.yml up -d

# View logs
docker compose -f docker-compose.test.yml logs -f

# Stop the service
docker compose -f docker-compose.test.yml down
```

## Required Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `QUARKUS_HTTP_PORT` | `8080` | HTTP server port inside container |
| `QUARKUS_DATASOURCE_JDBC_URL` | - | MySQL JDBC connection URL |
| `QUARKUS_DATASOURCE_USERNAME` | `pipeline` | Database username |
| `QUARKUS_DATASOURCE_PASSWORD` | `password` | Database password |
| `QUARKUS_DATASOURCE_REACTIVE_URL` | - | MySQL reactive connection URL |
| `PIPELINE_CONSUL_HOST` | `localhost` | Consul hostname |
| `PIPELINE_CONSUL_PORT` | `8500` | Consul port |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:9092` | Kafka brokers |
| `MP_MESSAGING_CONNECTOR_SMALLRYE_KAFKA_APICURIO_REGISTRY_URL` | - | Apicurio Registry URL for Kafka schema |

## Health Checks

```bash
# Readiness check
curl http://localhost:38101/q/health/ready

# Liveness check
curl http://localhost:38101/q/health/live

# Full health status
curl http://localhost:38101/q/health

# Metrics
curl http://localhost:38101/q/metrics
```

## Logs

```bash
# View logs (docker run)
docker logs -f platform-registration-service

# View logs (docker-compose)
docker compose -f docker-compose.test.yml logs -f
```

## Troubleshooting

### Container exits immediately
Check the logs for errors:
```bash
docker logs platform-registration-service
```

Common issues:
- MySQL not accessible → Verify `pipeline-mysql` container is running
- Consul not accessible → Verify `pipeline-consul` container is running
- Database migration fails → Check MySQL credentials and database exists

### Can't connect to infrastructure
Verify network:
```bash
# Check network exists
docker network ls | grep pipeline-test-network

# Check infrastructure containers
docker ps | grep pipeline-

# Inspect network
docker network inspect pipeline-test-network
```

### Port conflicts
If port 38101 is in use:
```bash
# Check what's using the port
lsof -i :38101

# Or run on different host port
docker run -p 38102:8080 ...
```

## Stop and Clean Up

```bash
# Stop container
docker stop platform-registration-service

# Remove container
docker rm platform-registration-service

# Or with docker-compose
docker compose -f docker-compose.test.yml down
```

