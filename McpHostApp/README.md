# MCP Host App

A reference client application that demonstrates how to use the [SpringPluggableMcp](../SpringPluggableMcp) library to build an MCP server with both static and dynamic tools.

## Running

### With Docker Compose (recommended)

From the parent directory:

```bash
docker compose up -d --build
```

This starts:

| Service | Port | Description |
|---|---|---|
| `mcp-tools-db` | 5432 | PostgreSQL with seed data |
| `mcp-server` | 8080 | This application |
| `mcp-inspector` | 6274 | MCP Inspector UI |

Connect the inspector at `http://localhost:6274` using transport **Streamable HTTP** and URL `http://mcp-server:8080/mcp`.

### Locally

```bash
# Start PostgreSQL (or use the docker-compose postgres service)
# Then:
mvn spring-boot:run
```

### Database Connection

```
URL:      jdbc:postgresql://localhost:5432/mcp_tools
User:     postgres
Password: postgres
```

## How It All Comes Together

There are three ways to expose tools through the MCP server. This app demonstrates all three.

```
                        MCP Server (:8080/mcp)
                                │
          ┌─────────────────────┼──────────────────────┐
          │                     │                      │
    1. Static Tools      2. Dynamic Tools         3. Dynamic Tools
    (@Tool in code)      (from sources)           (from sources)
          │                     │                      │
    Auto-discovered      ToolDefinitionSource    ToolDefinitionSource
    by the library       beans load records      beans load records
          │                     │                      │
    Executed directly    Each record has          Each record has
    in Java              executorType ──┐         executorType ──┐
                                        │                        │
                         ┌──────────────┘         ┌──────────────┘
                         │                        │
                  ToolExecutionStrategy    ToolExecutionStrategy
                  bean matched by type     bean matched by type
                         │                        │
                  executorConfig tells     executorConfig tells
                  the strategy HOW         the strategy HOW
```

**The three pieces for dynamic tools:**

1. **Source** (`ToolDefinitionSource`) — where tool definitions come from. Multiple sources can coexist. Each is a `@Component` bean.
2. **Record** (`DynamicToolRecord`) — what a source returns: `name`, `description`, `inputSchema` (exposed to MCP clients) + `executorType`, `executorConfig` (server-side routing).
3. **Strategy** (`ToolExecutionStrategy`) — how a tool executes when called. Matched by `executorType`. Receives the MCP client's input + the record's `executorConfig`.

## 1. Static Tools (code-defined)

Any `@Component` with `@Tool` methods is auto-discovered by the library. No registration needed. Execution logic lives directly in the method.

```
mcp/tools/
├── HostTools.java              # Simple ping/pong
├── TextTools.java              # Text analysis and transformation
├── TimeTools.java              # Timezone queries
└── WikipediaTodayTools.java    # HTTP call to Wikipedia API
```

**Example: a static tool that calls an external API**

```java
@Component
public class WikipediaTodayTools {

    private final RestClient restClient;

    public WikipediaTodayTools(ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.wikimedia.org/feed/v1/wikipedia/en")
                .build();
    }

    @Tool(name = "today_in_history",
          description = "Get historical events for a given date from Wikipedia")
    public String todayInHistory(
            @ToolParam(description = "Month (1-12)") Integer month,
            @ToolParam(description = "Day (1-31)") Integer day) {

        String json = restClient.get()
                .uri("/onthisday/all/{month}/{day}", month, day)
                .retrieve()
                .body(String.class);
        // parse and return
    }
}
```

No `executorType`, no `executorConfig`, no strategy — the tool owns its own execution.

## 2. Tool Definition Sources

Sources tell the library where to find dynamic tool definitions. This app has two sources running side by side — each is an independent `@Component` that implements `ToolDefinitionSource`.

```
mcp/source/
├── JdbcToolDefinitionSource.java       # Loads from PostgreSQL
└── InMemoryToolDefinitionSource.java   # Loads from a hardcoded list
```

The library's `DefaultDynamicToolLoader` collects all `ToolDefinitionSource` beans and calls each one at startup. If one fails, the others still load. On refresh, each source is polled independently.

### JdbcToolDefinitionSource

Loads tools from a two-table schema (`tool_definitions` + `tool_registry`) with per-server assignment:

```java
@Component
public class JdbcToolDefinitionSource implements ToolDefinitionSource {

    @Override
    public List<DynamicToolRecord> loadAll() {
        return jdbcTemplate.query("""
                SELECT td.tool_name, td.tool_description, td.input_schema,
                       td.executor_type, td.executor_config
                FROM tool_registry tr
                JOIN tool_definitions td ON td.id = tr.tool_id
                WHERE tr.server_id = ? AND tr.enabled = true
                """,
                (rs, rowNum) -> new DynamicToolRecord(
                        rs.getString("tool_name"),
                        rs.getString("tool_description"),
                        rs.getString("input_schema"),
                        rs.getString("executor_type"),
                        rs.getString("executor_config")
                ),
                serverId);
    }

    @Override
    public List<DynamicToolRecord> loadSince(Instant since) {
        // same query with AND (td.updated_at > ? OR tr.updated_at > ?)
    }
}
```

### InMemoryToolDefinitionSource

Loads tools from a hardcoded list. Demonstrates that sources don't need a database — they can come from anywhere (REST API, config file, service registry, etc.):

```java
@Component
public class InMemoryToolDefinitionSource implements ToolDefinitionSource {

    @Override
    public List<DynamicToolRecord> loadAll() {
        return List.of(
                new DynamicToolRecord(
                        "server_info",
                        "Return basic information about this MCP server",
                        "{\"type\":\"object\",\"properties\":{}}",
                        "echo",
                        "{\"message\":\"MCP Host App v1.0.0\"}"
                )
        );
    }

    // loadSince not overridden — defaults to returning empty list
}
```

### Configuration

The `application.yml` only needs JDBC connection info (for the JDBC source) and refresh config:

```yaml
dynamic-tools:
  jdbc:
    datasource:
      url: ${DYNAMIC_TOOLS_DB_URL:jdbc:postgresql://localhost:5432/mcp_tools}
      username: ${DYNAMIC_TOOLS_DB_USERNAME:postgres}
      password: ${DYNAMIC_TOOLS_DB_PASSWORD:postgres}
      driver-class-name: org.postgresql.Driver
  refresh:
    enabled: true
    interval: 5m
```

## 3. Execution Strategies

When a dynamic tool is called, the library looks up the `ToolExecutionStrategy` bean whose `getType()` matches the record's `executorType`, then calls `execute(toolInput, executorConfig)`.

- **`toolInput`** — JSON string with the arguments the MCP client sent at runtime
- **`executorConfig`** — JSON string from the tool definition, set at registration time

```
mcp/execution/
├── EchoStrategy.java           # type: "echo"
├── HttpStrategy.java           # type: "http"
└── IdaasHttpStrategy.java      # type: "http_idaas"
```

This app demonstrates three execution patterns:

### Pattern A: Local Execution

The strategy runs logic directly in-process. No external calls.

```java
@Component
public class EchoStrategy implements ToolExecutionStrategy {

    @Override
    public String getType() { return "echo"; }

    @Override
    public String execute(String toolInput, String executorConfig) {
        return "Echo: " + toolInput + " | Config: " + executorConfig;
    }
}
```

A tool using this:

| Field | Value |
|---|---|
| `executor_type` | `echo` |
| `executor_config` | `{"prefix": "[McpHostApp]"}` |

The strategy receives both and decides what to do. The `executorConfig` is optional context — some strategies ignore it entirely.

### Pattern B: Generic HTTP Execution

A single strategy handles many tools. Each tool's `executorConfig` tells it the URL, HTTP method, headers, and body template.

```java
@Component
public class HttpStrategy implements ToolExecutionStrategy {

    @Override
    public String getType() { return "http"; }

    @Override
    public String execute(String toolInput, String executorConfig) {
        // executorConfig has: url, method, headers, bodyTemplate
        // toolInput has: the arguments from the MCP client
        // Strategy resolves {placeholders} in the URL and body using input values
    }
}
```

Tools using this strategy with different configs:

| Tool | `executor_config` |
|---|---|
| `get_github_user` | `{"url":"https://api.github.com/users/{username}","method":"GET"}` |
| `create_pastebin` | `{"url":"https://jsonplaceholder.typicode.com/posts","method":"POST","bodyTemplate":{"title":"{title}"}}` |
| `get_random_activity` | `{"url":"https://bored-api.appbrewery.com/random","method":"GET"}` |

Same strategy, different behavior — driven by config, not code.

### Pattern C: Pre-configured HTTP Client

A strategy with shared auth, base URL, and timeouts baked into a Spring-managed `RestClient`. Tools only need to specify the relative path.

```java
@Component
public class IdaasHttpStrategy implements ToolExecutionStrategy {

    private final RestClient restClient;

    public IdaasHttpStrategy(
            @Value("${idaas.base-url}") String baseUrl,
            @Value("${idaas.api-key}") String apiKey,
            ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Api-Key", apiKey)
                .build();
    }

    @Override
    public String getType() { return "http_idaas"; }

    @Override
    public String execute(String toolInput, String executorConfig) {
        // executorConfig has: path, method
        // restClient already has base URL + auth
    }
}
```

Tools using this strategy:

| Tool | `executor_config` |
|---|---|
| `idaas_get_user` | `{"path":"/users/{id}","method":"GET"}` |
| `idaas_create_post` | `{"path":"/posts","method":"POST"}` |

The difference from Pattern B: the strategy owns the connection setup (auth, base URL, timeouts). The admin only configures paths per tool, not full URLs with credentials.

## Registered Tools

### Static (6 tools, code-defined)

| Tool | Description |
|---|---|
| `calculate` | Basic arithmetic (library showcase) |
| `analyze_text` | Text statistics |
| `transform_text` | Text transformations |
| `get_current_time` | Current time in a timezone |
| `ping` | Simple ping/pong |
| `today_in_history` | Wikipedia "on this day" API |

### Dynamic from InMemoryToolDefinitionSource (2 tools)

| Tool | Executor | Description |
|---|---|---|
| `server_info` | `echo` | Server information |
| `random_number` | `echo` | Random number (demo) |

### Dynamic from JdbcToolDefinitionSource (8 tools)

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

## Project Structure

```
src/main/java/com/mcp/mcphostapp/
├── McpHostAppApplication.java
├── controller/
│   └── HealthController.java
├── service/
│   ├── TextService.java
│   └── TimeService.java
└── mcp/
    ├── config/
    │   └── McpAcceptHeaderFilter.java
    ├── execution/                          # ToolExecutionStrategy beans
    │   ├── EchoStrategy.java               #   Pattern A: local execution
    │   ├── HttpStrategy.java               #   Pattern B: generic HTTP
    │   └── IdaasHttpStrategy.java          #   Pattern C: pre-configured client
    ├── source/                             # ToolDefinitionSource beans
    │   ├── JdbcToolDefinitionSource.java   #   Loads from PostgreSQL
    │   └── InMemoryToolDefinitionSource.java   #   Loads from hardcoded list
    └── tools/                              # Static @Tool beans
        ├── HostTools.java
        ├── TextTools.java
        ├── TimeTools.java
        └── WikipediaTodayTools.java
```
