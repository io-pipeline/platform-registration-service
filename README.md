# Platform Registration Service

The Platform Registration Service is the central nervous system for service discovery and management in the pipeline platform. It acts as a comprehensive service registry, health monitoring system, and module management hub for all services and modules in the platform.

## Overview

This service provides real-time service discovery, health monitoring, and configuration management for the entire pipeline ecosystem. It maintains a live directory of all running services, their locations, capabilities, and health status while managing configuration schemas and publishing events for downstream systems.

### Core Functions

**Service Registration & Discovery**
- Central registry where services announce themselves
- Real-time directory of all running services with locations and capabilities
- Service resolution with filtering by tags and capabilities
- Support for both general services and specialized document processing modules

**Health Monitoring**
- Continuous health monitoring using gRPC health checks through Consul
- Automatic cleanup of unhealthy services
- Health status tracking and reporting
- Readiness probes for dependent services

**Module Management**
- Specialized handling for document processing modules
- Configuration schema validation and storage
- Module metadata management
- Integration with dynamic gRPC client factory

**Schema Management**
- Dual storage: MySQL as primary, Apicurio Registry as secondary
- OpenAPI/JSON Schema validation and versioning
- Automatic schema synchronization
- Schema discovery and retrieval

**Event Streaming**
- Publishes registration/unregistration events to Kafka
- Integration with OpenSearch for service indexing
- Protobuf-based event serialization
- Fire-and-forget event publishing

**Self-Registration**
- Automatically registers itself with Consul on startup
- Health check configuration and validation
- Service discovery integration

## Architecture

### Technology Stack

**Backend**
- **Framework**: Quarkus with reactive programming (Mutiny)
- **Database**: MySQL with Hibernate Reactive Panache
- **Service Discovery**: Consul integration with Vert.x client
- **Schema Registry**: Apicurio Registry v3
- **Messaging**: Kafka with SmallRye Reactive Messaging
- **API**: gRPC with Connect-RPC for web clients
- **Health**: MicroProfile Health with readiness probes
- **Metrics**: Prometheus integration
- **Logging**: JBoss LogManager with file rotation

**Frontend**
- **Framework**: Vue 3 with Composition API
- **UI Library**: Vuetify 3
- **Build Tool**: Vite
- **Integration**: Quinoa for seamless Quarkus integration
- **Routing**: Vue Router for SPA navigation

**Infrastructure**
- **Container**: Docker with UBI9/OpenJDK 21
- **Runtime**: Java 21 with reactive programming
- **Networking**: gRPC over HTTP with Connect-RPC

## Database Schema

### Modules Table
```sql
CREATE TABLE modules (
    service_id VARCHAR(255) PRIMARY KEY,
    service_name VARCHAR(255) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL,
    version VARCHAR(100),
    config_schema_id VARCHAR(255),
    metadata JSON,
    registered_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_heartbeat DATETIME(6),
    status ENUM('ACTIVE','INACTIVE','MAINTENANCE','UNHEALTHY') NOT NULL DEFAULT 'ACTIVE'
);
```

### Config Schemas Table
```sql
CREATE TABLE config_schemas (
    schema_id VARCHAR(255) PRIMARY KEY,
    service_name VARCHAR(255) NOT NULL,
    schema_version VARCHAR(100) NOT NULL,
    json_schema JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(255),
    apicurio_artifact_id VARCHAR(255),
    apicurio_global_id BIGINT,
    sync_status ENUM('FAILED','OUT_OF_SYNC','PENDING','SYNCED') NOT NULL DEFAULT 'PENDING',
    last_sync_attempt DATETIME(6),
    sync_error VARCHAR(255),
    CONSTRAINT unique_service_schema_version UNIQUE(service_name, schema_version)
);
```

## Building Locally

### Prerequisites
- Java 21 (Temurin, Oracle, or any compatible distribution)
- Docker (optional but required to run the container locally)
- Internet access to Maven Central and GitHub Packages
- GitHub Personal Access Token with `read:packages` (GitHub Packages requires auth even for public artifacts)

### Clone & Build
```bash
git clone https://github.com/io-pipeline/platform-registration-service.git
cd platform-registration-service

# Configure GitHub Packages credentials
export GITHUB_ACTOR=your-github-username
export GITHUB_TOKEN=your-personal-access-token

# Build the application (skip tests for a faster first run)
./gradlew clean build -x test
```

> **Tip:** Remove `-x test` once you have the required infrastructure (MySQL, Kafka, Consul) running locally or via Testcontainers.

### Build & Run the Docker Image
You can let Quarkus build the image or build it manually with Docker.

**Quarkus-managed build**
```bash
./gradlew clean build -x test \
  -Dquarkus.container-image.build=true \
  -Dquarkus.container-image.push=false \
  -Dquarkus.container-image.registry=ghcr.io \
  -Dquarkus.container-image.group=ai-pipestream \
  -Dquarkus.container-image.name=platform-registration-service \
  -Dquarkus.container-image.tag=local

docker run -p 38101:8080 ghcr.io/ai-pipestream/platform-registration-service:local
```

**Manual docker build**
```bash
./gradlew clean quarkusBuild -x test
docker build -f src/main/docker/Dockerfile.jvm -t platform-registration-service:local .
docker run -p 38101:8080 platform-registration-service:local
```

### Troubleshooting
- **401 from GitHub Packages** – double-check `GITHUB_ACTOR`/`GITHUB_TOKEN`.
- **Docker build fails** – ensure `./gradlew quarkusBuild` succeeded so that `build/quarkus-app/` exists.
- **Tests fail** – services like MySQL, Kafka, and Consul must be available; skip tests until you’re ready to configure them.

## Docker Build Process

### Prerequisites
- Docker installed and running
- Java 21+ and Gradle for building the application
- Access to Red Hat UBI9 base images

### Build Steps

**1. Build the Application**
```bash
# From the project root
./gradlew :applications:platform-registration-service:build
```

**2. Build Docker Image**
```bash
# Navigate to the service directory
cd applications/platform-registration-service

# Build the Docker image
docker build -f src/main/docker/Dockerfile.jvm -t platform-registration-service:latest .
```

**3. Alternative Build with Custom Tag**
```bash
docker build -f src/main/docker/Dockerfile.jvm -t your-registry/platform-registration-service:v1.0.0 .
```

### Dockerfile Details

**Base Image**: `registry.access.redhat.com/ubi9/openjdk-21:1.21`

**Multi-layer Structure**:
- **Dependencies Layer**: `/deployments/lib/` - Application JARs and dependencies
- **Application Layer**: `/deployments/*.jar` - Main application JARs
- **Resources Layer**: `/deployments/app/` - Application resources and configuration
- **Runtime Layer**: `/deployments/quarkus/` - Quarkus runtime components

**Security**:
- Runs as non-root user (UID 185)
- Minimal attack surface with UBI9 base
- No unnecessary packages or tools

**Port Configuration**:
- Exposes port 8080 by default
- Configurable via environment variables
- Supports both HTTP and gRPC on same port

### Runtime Configuration

**Environment Variables**:
```bash
# Consul Configuration
CONSUL_HOST=consul
CONSUL_PORT=8500

# JVM Configuration
JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

# Profile Configuration
QUARKUS_PROFILE=prod

# Database Configuration (if not using service discovery)
QUARKUS_DATASOURCE_JDBC_URL=jdbc:mysql://mysql:3306/pipeline_registry
QUARKUS_DATASOURCE_USERNAME=registration_user
QUARKUS_DATASOURCE_PASSWORD=password

# Kafka Configuration
KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# Apicurio Registry Configuration
APICURIO_REGISTRY_URL=http://apicurio:8081/apis/registry/v3
```

**Memory Management**:
- Automatic heap sizing based on container memory limits
- Configurable via `JAVA_MAX_MEM_RATIO` (default: 50%)
- Initial heap sizing via `JAVA_INITIAL_MEM_RATIO` (default: 25%)

**Health Checks**:
```bash
# Readiness check
curl http://localhost:8080/q/health/ready

# Liveness check  
curl http://localhost:8080/q/health/live

# Metrics endpoint
curl http://localhost:8080/q/metrics
```

### Docker Compose Example

```yaml
version: '3.8'
services:
  platform-registration-service:
    image: platform-registration-service:latest
    ports:
      - "38101:8080"
    environment:
      - CONSUL_HOST=consul
      - CONSUL_PORT=8500
      - QUARKUS_PROFILE=prod
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - APICURIO_REGISTRY_URL=http://apicurio:8081/apis/registry/v3
    depends_on:
      - consul
      - mysql
      - kafka
      - apicurio
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/q/health/ready"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
```

## Development

### Local Development Setup

**Prerequisites**:
- Java 21+
- Node.js 22+ and pnpm
- Docker and Docker Compose
- Gradle

**Start Dependencies**:
```bash
# Start infrastructure services
./scripts/start-infrastructure.sh
```

**Run in Development Mode**:
```bash
# Start the service in Quarkus dev mode
./gradlew :applications:platform-registration-service:quarkusDev
```

**Frontend Development**:
```bash
# Install dependencies
cd applications/platform-registration-service/src/main/ui-vue
pnpm install

# Start development server
pnpm run dev
```

### Testing

**Run Tests**:
```bash
# Run all tests
./gradlew :applications:platform-registration-service:test

# Run specific test class
./gradlew :applications:platform-registration-service:test --tests "io.pipeline.registration.handlers.*Test"
```

**Integration Testing**:
- Uses Testcontainers for MySQL
- Mock Consul and Apicurio for unit tests
- End-to-end tests with real infrastructure

## API Reference

### gRPC Services

**PlatformRegistration Service**:
- `registerService(ServiceRegistrationRequest) -> Multi<RegistrationEvent>`
- `registerModule(ModuleRegistrationRequest) -> Multi<RegistrationEvent>`
- `unregisterService(UnregisterRequest) -> Uni<UnregisterResponse>`
- `unregisterModule(UnregisterRequest) -> Uni<UnregisterResponse>`
- `listServices(Empty) -> Uni<ServiceListResponse>`
- `listModules(Empty) -> Uni<ModuleListResponse>`
- `getService(ServiceLookupRequest) -> Uni<ServiceDetails>`
- `getModule(ServiceLookupRequest) -> Uni<ModuleDetails>`
- `resolveService(ServiceResolveRequest) -> Uni<ServiceResolveResponse>`
- `watchServices(Empty) -> Multi<ServiceListResponse>`

### REST Endpoints

**Health Checks**:
- `GET /q/health/ready` - Readiness probe
- `GET /q/health/live` - Liveness probe
- `GET /q/metrics` - Prometheus metrics

**Management**:
- `GET /swagger-ui` - API documentation
- `GET /platform-registration/` - Web UI

## Monitoring & Observability

### Metrics
- Service registration/unregistration counts
- Health check success/failure rates
- Database connection pool metrics
- Consul client metrics
- Apicurio Registry sync status

### Logging
- Structured logging with JSON format
- File rotation with size limits
- Log levels configurable per package
- Request/response logging for gRPC calls

### Health Checks
- **Dependent Services**: MySQL, Consul, Apicurio Registry
- **Service Health**: gRPC health service integration
- **Readiness**: All dependencies must be healthy
- **Liveness**: Application must be responding

## Configuration

### Application Properties

**Core Configuration**:
```properties
# Service Configuration
quarkus.application.name=platform-registration-service
quarkus.http.port=38101
quarkus.grpc.server.use-separate-server=false

# Database
quarkus.datasource.db-kind=mysql
quarkus.hibernate-orm.schema-management.strategy=none
quarkus.flyway.migrate-at-start=true

# Consul
pipeline.consul.enabled=true
pipeline.consul.host=localhost
pipeline.consul.port=8500

# Apicurio Registry
apicurio.registry.url=http://localhost:8081

# Kafka
kafka.bootstrap.servers=localhost:9092
```

**Profiles**:
- `dev` - Development with hot reload and debug logging
- `test` - Testing with embedded services
- `prod` - Production with optimized settings
- `docker` - Container-specific configuration

## Troubleshooting

### Common Issues

**Service Registration Fails**:
- Check Consul connectivity
- Verify service health endpoints
- Review gRPC health service implementation

**Database Connection Issues**:
- Verify MySQL is running and accessible
- Check database credentials and permissions
- Review Flyway migration status

**Schema Sync Failures**:
- Check Apicurio Registry connectivity
- Verify schema format and validation
- Review sync status in database

**Frontend Not Loading**:
- Check Quinoa build process
- Verify Vue.js dependencies
- Review browser console for errors

### Debug Mode

**Enable Debug Logging**:
```properties
quarkus.log.level=DEBUG
quarkus.log.category."io.pipeline.registration".level=TRACE
```

**Remote Debugging**:
```bash
docker run -e JAVA_DEBUG=true -e JAVA_DEBUG_PORT=*:5005 -p 5005:5005 platform-registration-service
```

## Contributing

### Code Style
- Java 21 with Quarkus reactive programming
- 4-space indentation
- Package naming: `io.pipeline.registration.*`
- Class naming: PascalCase
- Method/field naming: camelCase

### Testing Requirements
- Unit tests for all business logic
- Integration tests for external dependencies
- Frontend tests for Vue components
- End-to-end tests for critical paths

### Pull Request Process
1. Create feature branch from main
2. Implement changes with tests
3. Update documentation if needed
4. Run full test suite
5. Submit PR with clear description

## License

This project is part of the Pipeline Platform and follows the same licensing terms as the main project.