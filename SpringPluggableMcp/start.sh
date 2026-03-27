#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
HOST_APP_DIR="${1:-/Users/carlos/JavaProjects/McpHostApp}"

echo "=== Step 1: Start Postgres ==="
docker compose -f "$SCRIPT_DIR/docker-compose.yml" up -d
echo "Waiting for Postgres to be healthy..."
until docker inspect --format='{{.State.Health.Status}}' mcp-tools-db 2>/dev/null | grep -q healthy; do
  sleep 1
done
echo "Postgres is ready."

echo ""
echo "=== Step 2: Build & install SpringPluggableMcp library ==="
mvn -f "$SCRIPT_DIR/pom.xml" clean install -DskipTests -q
echo "Library installed to local Maven repo."

echo ""
echo "=== Step 3: Build McpHostApp with latest library ==="
mvn -f "$HOST_APP_DIR/pom.xml" clean compile -q
echo "Host app compiled."

echo ""
echo "=== Step 4: Start McpHostApp ==="
mvn -f "$HOST_APP_DIR/pom.xml" spring-boot:run
