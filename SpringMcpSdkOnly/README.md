# Spring Boot MCP (SDK Only)

A framework for building MCP (Model Context Protocol) servers with Spring Boot using the **MCP Java SDK directly** — no Spring AI dependency. Consists of a reusable library and a reference client application.

This is the SDK-only variant of [SpringAIMCP](../SpringAIMCP). Both projects are functionally equivalent, but this one replaces all Spring AI abstractions (`@Tool`, `ToolCallback`, `ToolDefinition`, `MethodToolCallbackProvider`, `McpToolUtils`, and the Spring AI auto-configuration) with custom implementations that depend only on the MCP Java SDK (`io.modelcontextprotocol.sdk`).

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
| `mcp-server` | 8790 | MCP server (McpHostApp) |
| `mcp-inspector` | 6274 | MCP Inspector UI |

## Endpoints

| URL | Description |
|---|---|
| `http://localhost:8790/mcp` | MCP Streamable HTTP endpoint |
| `http://localhost:8790/actuator/mcptools` | Tool dashboard (all registered tools + source health) |
| `http://localhost:8790/health` | Health check |
| `http://localhost:6274` | MCP Inspector (connect with Streamable HTTP to `http://mcp-server:8790/mcp`) |

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

## What Changed from SpringAIMCP

| Concern | SpringAIMCP (Spring AI) | SpringMcpSdkOnly (this project) |
|---|---|---|
| MCP dependency | `spring-ai-starter-mcp-server-webmvc` | `io.modelcontextprotocol.sdk:mcp-spring-webmvc` |
| Tool annotation | `@Tool` / `@ToolParam` (Spring AI) | `@McpTool` / `@McpToolParam` (custom) |
| Tool callback | `org.springframework.ai.tool.ToolCallback` | `com.mcp.springpluggablemcp.tool.ToolCallback` (custom) |
| Tool definition | `org.springframework.ai.tool.definition.ToolDefinition` | `com.mcp.springpluggablemcp.tool.ToolDefinition` (custom) |
| Tool scanner | `MethodToolCallbackProvider` (Spring AI) | `MethodToolScanner` (custom, reflection-based) |
| MCP conversion | `org.springframework.ai.mcp.McpToolUtils` | `com.mcp.springpluggablemcp.tool.McpToolUtils` (custom) |
| Server setup | Spring AI auto-configuration | `McpServerConfig` (manual `McpSyncServer` + transport beans) |
| Server config | `spring.ai.mcp.server.*` | `spring-pluggable-mcp.server.*` |

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
