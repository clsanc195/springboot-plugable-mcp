# Spring Boot MCP

A framework for building MCP (Model Context Protocol) servers with Spring Boot. Consists of a reusable library and a reference client application.

## Projects

| Project | Description |
|---|---|
| [SpringPluggableMcp](./SpringPluggableMcp) | Library that turns any Spring Boot app into an MCP server with pluggable tool loading and execution |
| [McpHostApp](./McpHostApp) | Reference client demonstrating static tools, dynamic tool sources, and execution strategies |

## Quick Start

```bash
docker compose up -d --build
```

This starts:

| Service | Port | Description |
|---|---|---|
| `mcp-tools-db` | 5432 | PostgreSQL 17 with seed data |
| `mcp-server` | 8080 | MCP server (McpHostApp) |
| `mcp-inspector` | 6274 | MCP Inspector UI |

## Endpoints

| URL | Description |
|---|---|
| `http://localhost:8080/mcp` | MCP Streamable HTTP endpoint |
| `http://localhost:8080/actuator/mcptools` | Tool dashboard (all registered tools + source health) |
| `http://localhost:8080/health` | Health check |
| `http://localhost:6274` | MCP Inspector (connect with Streamable HTTP to `http://mcp-server:8080/mcp`) |

## Database

```
Host:     localhost:5432
Database: mcp_tools
User:     postgres
Password: postgres
```

## Prerequisites

- Docker and Docker Compose
- Java 21+ and Maven 3.9+ (for local development)

## Local Development

```bash
# Build the library and install to local Maven repo
cd SpringPluggableMcp && mvn install -DskipTests

# Build and run the host app
cd ../McpHostApp && mvn spring-boot:run
```

Requires a running PostgreSQL instance -- use `docker compose up -d postgres` to start just the database.

## Project Structure

```
.
├── docker-compose.yml          # Orchestrates all services
├── Dockerfile                  # Multi-stage build (library + host app)
├── db/
│   └── init.sql                # Database schema and seed data
├── inspector-entrypoint.sh     # MCP Inspector startup patch
├── SpringPluggableMcp/         # Library
└── McpHostApp/                 # Client
```
