#!/bin/bash

# Platform Registration Service Startup Script
# Port: 38101 (Core Service)
# This script helps start the platform registration service in development mode
# with proper environment variable detection and instance management.
#
# Bootstrap Design: Like Gradle Wrapper
# - Automatically downloads helper scripts from GitHub if not available locally
# - Works OOTB on Windows/Mac/Linux right after checkout
# - No manual dev-assets setup required

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/.."

# ============================================================================
# Bootstrap Helper Scripts from GitHub (like gradlew)
# ============================================================================

DEV_ASSETS_REPO="https://raw.githubusercontent.com/ai-pipestream/dev-assets/main"
HELPERS_DIR="$PROJECT_ROOT/.dev-helpers"
DEV_ASSETS_LOCATION="${DEV_ASSETS_LOCATION:-$HELPERS_DIR}"

bootstrap_helpers() {
  # Check if local dev-assets checkout exists
  if [ -d "/home/krickert/IdeaProjects/gitea/dev-assets" ]; then
    DEV_ASSETS_LOCATION="/home/krickert/IdeaProjects/gitea/dev-assets"
    return 0
  fi

  # Check if already bootstrapped
  if [ -f "$HELPERS_DIR/shared-utils.sh" ]; then
    DEV_ASSETS_LOCATION="$HELPERS_DIR"
    return 0
  fi

  # Bootstrap from GitHub
  echo "🔄 Bootstrapping helper scripts from GitHub..."
  mkdir -p "$HELPERS_DIR"

  # Download shared-utils.sh
  if curl -fsSL "$DEV_ASSETS_REPO/scripts/shared-utils.sh" -o "$HELPERS_DIR/shared-utils.sh" 2>/dev/null; then
    chmod +x "$HELPERS_DIR/shared-utils.sh"
    echo "✓ Downloaded helper scripts"
    DEV_ASSETS_LOCATION="$HELPERS_DIR"
  else
    echo "⚠️  Could not download from dev-assets repo, using minimal fallbacks"
    create_minimal_helpers
    DEV_ASSETS_LOCATION="$HELPERS_DIR"
  fi
}

create_minimal_helpers() {
  # Create minimal shared-utils.sh with essential functions
  cat > "$HELPERS_DIR/shared-utils.sh" << 'EOF'
#!/bin/bash
# Minimal fallback helper functions

check_dependencies() {
  for cmd in "$@"; do
    command -v "$cmd" >/dev/null 2>&1 || { echo "ERROR: $cmd is required but not installed"; exit 1; }
  done
}

check_port() {
  lsof -i ":$1" >/dev/null 2>&1
}

kill_process_on_port() {
  local port=$1
  local pid=$(lsof -t -i ":$port" 2>/dev/null)
  [ -n "$pid" ] && kill "$pid" 2>/dev/null
}

validate_project_structure() {
  for file in "$@"; do
    [ ! -f "$file" ] && { echo "ERROR: Expected file not found: $file"; exit 1; }
  done
}

print_status() {
  local level=$1
  shift
  case "$level" in
    header) echo "==========================================" ;;
    info) echo "ℹ️  $*" ;;
    warning) echo "⚠️  $*" ;;
    error) echo "❌ $*" ;;
  esac
}

set_registration_host() {
  local service_name=$1
  local env_var=$2
  export $env_var="${!env_var:-localhost}"
}
EOF
  chmod +x "$HELPERS_DIR/shared-utils.sh"
}

# Bootstrap the helpers
bootstrap_helpers

# Source shared utilities
source "$DEV_ASSETS_LOCATION/scripts/shared-utils.sh"

# Check dependencies
check_dependencies "docker" "java"

# Service configuration
SERVICE_NAME="Platform Registration Service"
SERVICE_PORT="38101"
DESCRIPTION="Module registry & health management"

# Validate we're in the correct directory
validate_project_structure "build.gradle" "src/main/resources/application.properties"

# Set environment variables
export QUARKUS_HTTP_PORT="$SERVICE_PORT"

# Set registration host using Docker bridge detection
set_registration_host "platform-registration" "PLATFORM_REGISTRATION_HOST"

# Set Consul configuration (can be overridden)
export PIPELINE_CONSUL_HOST="${PIPELINE_CONSUL_HOST:-localhost}"
export PIPELINE_CONSUL_PORT="${PIPELINE_CONSUL_PORT:-8500}"

print_status "header" "Starting $SERVICE_NAME"
print_status "info" "Port: $SERVICE_PORT"
print_status "info" "Description: $DESCRIPTION"
print_status "info" "Dev Assets Location: $DEV_ASSETS_LOCATION"
print_status "info" "Configuration:"
echo "  Service Host: $PLATFORM_REGISTRATION_HOST"
echo "  HTTP/gRPC Port: $QUARKUS_HTTP_PORT"
echo "  Consul Host: $PIPELINE_CONSUL_HOST"
echo "  Consul Port: $PIPELINE_CONSUL_PORT"
echo

# Check if already running and offer to kill
if check_port "$SERVICE_PORT" "$SERVICE_NAME"; then
    print_status "warning" "$SERVICE_NAME is already running on port $SERVICE_PORT."
    read -p "Would you like to kill the existing process and restart? (y/N) " -r response
    if [[ "$response" =~ ^[Yy]$ ]]; then
        kill_process_on_port "$SERVICE_PORT" "$SERVICE_NAME"
    else
        print_status "info" "Cancelled by user."
        exit 0
    fi
fi

print_status "info" "Starting $SERVICE_NAME in Quarkus dev mode..."
print_status "info" "DevServices will automatically start: MySQL, Consul, etc."
print_status "info" "Press Ctrl+C to stop"
echo

# Start using the app's own gradlew
./gradlew quarkusDev