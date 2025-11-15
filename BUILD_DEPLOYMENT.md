# Platform Registration Service - Build and Deployment Guide

## Overview

This document outlines the complete build, versioning, and deployment pipeline for the platform-registration-service. The pipeline follows a standardized approach that can be replicated across all platform services.

## Build Pipeline Architecture

The pipeline consists of multiple jobs that handle different aspects of the build and deployment process:

### 1. Build Job
- **Trigger**: Push to main, PR to main, manual workflow dispatch
- **Purpose**: Compile, test, and validate the codebase
- **Outputs**: Test results, build artifacts
- **Key Features**:
  - Full history checkout for versioning
  - JDK 21 with Temurin distribution
  - Gradle caching for performance
  - Test execution with results upload

### 2. Snapshot Publishing Job
- **Trigger**: Push to main (not manual dispatch)
- **Purpose**: Publish SNAPSHOT versions to Maven Central
- **Dependencies**: Requires successful build
- **Key Features**:
  - GPG signing (if configured)
  - Maven Central snapshots repository
  - Automatic snapshot versioning via axion-release

### 3. Release Job
- **Trigger**: Manual workflow dispatch only
- **Purpose**: Create production releases with versioning
- **Dependencies**: Requires successful build
- **Key Features**:
  - Manual version bump selection (major/minor/patch/manual)
  - Git tag creation and push
  - Maven Central release publishing
  - GitHub release creation with assets
  - Release artifact packaging

### 4. Production Docker Job
- **Trigger**: Successful release completion
- **Purpose**: Build and publish production Docker images
- **Dependencies**: Requires successful release
- **Key Features**:
  - Uses release tag for checkout
  - Builds production-optimized images
  - Pushes to GitHub Container Registry (GHCR)
  - Tags with version and 'latest'

### 5. Snapshot Docker Job
- **Trigger**: Push to main (not manual dispatch)
- **Purpose**: Build and publish snapshot Docker images
- **Dependencies**: Requires successful build and snapshot publishing
- **Key Features**:
  - Uses commit SHA for tagging
  - Pushes to GitHub Container Registry (GHCR)
  - Multiple tags for traceability

## Versioning Strategy

The service uses [axion-release](https://axion-release-plugin.readthedocs.io/) for automatic versioning:

### Version Format
- **Snapshots**: `X.Y.Z-SNAPSHOT` (e.g., `1.2.3-SNAPSHOT`)
- **Releases**: `X.Y.Z` (e.g., `1.2.3`)

### Version Bump Types
- **Major**: Increments X.0.0 (breaking changes)
- **Minor**: Increments X.Y.0 (new features)
- **Patch**: Increments X.Y.Z (bug fixes)
- **Manual**: Custom version entry

### Version Determination
- Versions are calculated from Git tags with 'v' prefix
- Current version determined by: `./gradlew currentVersion`
- Release versions calculated by incrementing based on bump type

## Workflow Triggers

### Automatic Triggers
- **Push to main**: Triggers build + snapshot publishing + snapshot Docker
- **Pull Request to main**: Triggers build only

### Manual Triggers
- **Workflow Dispatch**: Triggers full release pipeline
  - Choose version bump type
  - Optional custom version for manual entry

## Required Secrets

### Maven Central Publishing
```
MAVEN_CENTRAL_USERNAME    # Sonatype OSSRH username
MAVEN_CENTRAL_PASSWORD    # Sonatype OSSRH password
GPG_SIGNING_KEY          # Base64 encoded GPG private key
GPG_SIGNING_PASSPHRASE   # GPG key passphrase
```

### GitHub Container Registry
```
GITHUB_TOKEN             # Automatically provided by GitHub
```

### Optional: Docker Hub Publishing
```
DOCKERHUB_USERNAME       # Docker Hub username
DOCKERHUB_TOKEN          # Docker Hub access token
```

## Docker Images

### Production Images
- **Registry**: `ghcr.io`
- **Repository**: `{owner}/platform-registration-service`
- **Tags**:
  - `{version}` (e.g., `1.2.3`)
  - `latest`

### Snapshot Images
- **Registry**: `ghcr.io`
- **Repository**: `{owner}/platform-registration-service`
- **Tags**:
  - `main-{short-sha}` (e.g., `main-abc1234`)
  - `{short-sha}` (e.g., `abc1234`)

## Release Process

### 1. Prepare Release
- Ensure all changes are merged to main
- Run tests locally: `./gradlew clean build test`
- Update documentation if needed

### 2. Trigger Release
1. Go to GitHub Actions → "Build and Publish"
2. Click "Run workflow"
3. Select version bump type:
   - `patch`: Bug fixes (1.2.3 → 1.2.4)
   - `minor`: New features (1.2.3 → 1.3.0)
   - `major`: Breaking changes (1.2.3 → 2.0.0)
   - `manual`: Custom version
4. For manual: Enter desired version (e.g., `1.2.3-rc.1`)

### 3. Monitor Pipeline
The pipeline will:
1. ✅ Build and test code
2. ✅ Create and push Git tag
3. ✅ Publish to Maven Central
4. ✅ Create GitHub release with assets
5. ✅ Build and push production Docker image

### 4. Verify Release
- Check GitHub releases for new version
- Verify Docker image availability on GHCR
- Confirm Maven Central publication

## GitHub Releases

Each release includes:
- **Release Notes Template**: Pre-filled with Docker image references
- **Release Assets**: JAR files, documentation
- **Automatic Changelog**: Generated from commits

## Docker Deployment

### Using Production Images
```bash
# Pull and run production image
docker run -d \
  --name platform-registration-service \
  -p 8080:8080 \
  ghcr.io/ai-pipestream/platform-registration-service:latest
```

### Using Specific Versions
```bash
# Use specific version
docker run -d \
  --name platform-registration-service \
  -p 8080:8080 \
  ghcr.io/ai-pipestream/platform-registration-service:1.2.3
```

## Local Development

### Building Locally
```bash
# Full build with tests
./gradlew clean build test

# Build without tests
./gradlew clean build -x test

# Check current version
./gradlew currentVersion

# Publish to local Maven repo
./gradlew publishToMavenLocal
```

### Docker Build Locally
```bash
# Build Docker image locally
./gradlew clean build -x test \
  -Dquarkus.container-image.build=true \
  -Dquarkus.container-image.push=false
```

## Troubleshooting

### Common Issues

#### Version Calculation Issues
- Ensure Git history is clean
- Check for existing version tags
- Verify axion-release configuration in build.gradle

#### Maven Central Publishing Failures
- Verify GPG key configuration
- Check Sonatype OSSRH credentials
- Ensure POM configuration is correct

#### Docker Build Failures
- Verify Quarkus container-image extension
- Check Docker Buildx setup
- Confirm registry permissions

#### Git Tag Conflicts
- Delete conflicting tags locally and remotely
- Ensure version bump is appropriate
- Check Git history for tag conflicts

### Pipeline Debugging

#### Check Build Logs
- Review GitHub Actions logs for each job
- Look for Gradle build failures
- Check test execution results

#### Manual Pipeline Steps
```bash
# Test version calculation
./gradlew currentVersion

# Test Maven publishing (dry run)
./gradlew publishToMavenLocal

# Test Docker build
./gradlew clean build -x test -Dquarkus.container-image.build=true
```

## Future Enhancements

### Planned Features
- Docker Hub publishing (see `docker.io-placeholder.yml`)
- Automated release notes generation
- Security scanning integration
- Multi-architecture Docker builds
- Blue/green deployment support

### Extending the Pipeline
- Add security scanning jobs
- Include performance testing
- Add integration test environments
- Implement automated rollback capabilities

## Maintenance

### Regular Tasks
- Update dependencies quarterly
- Review and rotate secrets annually
- Monitor pipeline performance
- Update GitHub Actions versions

### Security Considerations
- Rotate GPG keys regularly
- Use GitHub's secret scanning
- Implement branch protection rules
- Regular dependency vulnerability scans

---

## Quick Reference

### Trigger Release
1. GitHub Actions → "Build and Publish" → "Run workflow"
2. Select bump type → Run

### Check Version
```bash
./gradlew currentVersion
```

### Docker Images
- Production: `ghcr.io/{owner}/platform-registration-service:{version}`
- Latest: `ghcr.io/{owner}/platform-registration-service:latest`
- Snapshots: `ghcr.io/{owner}/platform-registration-service:main-{sha}`

### Release Assets
- JAR files in `build/libs/`
- Documentation in release assets
- Docker image references in release notes
