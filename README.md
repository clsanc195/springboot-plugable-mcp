# Spring Boot MCP POC

A proof of concept for building MCP (Model Context Protocol) servers with Spring Boot using a pluggable addon library.

## Projects

| Project | Description |
|---|---|
| [SpringPluggableMcp](./SpringPluggableMcp) | Library that turns any Spring Boot app into an MCP server with static and dynamic tool support |
| [McpHostApp](./McpHostApp) | Reference client application demonstrating how to use the library |

## Quick Start

```bash
docker compose up -d --build
```

This starts three services:

| Service | Port | Description |
|---|---|---|
| `mcp-tools-db` | 5432 | PostgreSQL 17 with seed data |
| `mcp-server` | 8080 | MCP server (McpHostApp) |
| `mcp-inspector` | 6274 | MCP Inspector UI |

## Connecting

### MCP Inspector

Open http://localhost:6274, select **Streamable HTTP**, enter `http://mcp-server:8080/mcp`, and click Connect.

### MCP Endpoint

```
POST http://localhost:8080/mcp
```

### Database

```
Host:     localhost:5432
Database: mcp_tools
User:     postgres
Password: postgres
```

## What Gets Registered

### Static Tools (code-defined)

| Tool | Description |
|---|---|
| `calculate` | Basic arithmetic |
| `analyze_text` | Text statistics |
| `transform_text` | Text transformations |
| `get_current_time` | Current time in a timezone |
| `ping` | Simple ping/pong |
| `today_in_history` | Wikipedia "on this day" API |

### Dynamic Tools (from database)

| Tool | Executor | Description |
|---|---|---|
| `echo_message` | `echo` | Echo with prefix |
| `lookup_user` | `echo` | User lookup |
| `greet_user` | `echo` | Greeting generator |
| `get_random_activity` | `http` | Bored API random activity |
| `get_github_user` | `http` | GitHub user profile |
| `create_pastebin` | `http` | JSONPlaceholder post creation |
| `idaas_get_user` | `http_idaas` | IDaaS user fetch |
| `idaas_create_post` | `http_idaas` | IDaaS post creation |

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

Requires a running PostgreSQL instance — use `docker compose up -d postgres` to start just the database.

## Project Structure

```
.
├── docker-compose.yml          # Orchestrates all services
├── Dockerfile                  # Multi-stage build (library + host app)
├── db/
│   └── init.sql                # Database schema and seed data
├── inspector-entrypoint.sh     # MCP Inspector startup patch
├── SpringPluggableMcp/         # Library — extension points and defaults
└── McpHostApp/                 # Client — tools, strategies, custom loading
```
