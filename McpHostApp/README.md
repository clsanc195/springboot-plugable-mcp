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

## What This App Demonstrates

### 1. Static Tools (code-defined)

Any `@Component` with `@Tool` methods is auto-discovered. No registration needed.

```
mcp/tools/
├── HostTools.java              # Simple ping/pong
├── TextTools.java              # Text analysis and transformation
├── TimeTools.java              # Timezone queries
└── WikipediaTodayTools.java    # HTTP call to Wikipedia API
```

**Example: a tool that makes an HTTP call**

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

### 2. Custom Tool Loading Source

This app overrides the library's default SQL loading by providing its own `ToolDefinitionSource` bean. The library's `SimpleQueryToolSource` backs off automatically.

```
mcp/source/
└── CustomToolDefinitionSource.java
```

The custom source uses a two-table schema (`tool_definitions` + `tool_registry`) to support per-server tool assignment:

```java
@Component
public class CustomToolDefinitionSource implements ToolDefinitionSource {

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
}
```

The `application.yml` only needs JDBC connection info and refresh config:

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

No `query`, `column-mapping`, or `delta-query` needed — the custom source owns all of that in code.

### 3. Execution Strategies

Each dynamic tool has an `executor_type` that routes it to a strategy bean, and an `executor_config` with per-tool configuration.

```
mcp/execution/
├── EchoStrategy.java           # type: "echo"     — echoes input back
├── HttpStrategy.java           # type: "http"      — generic HTTP calls
└── IdaasHttpStrategy.java      # type: "http_idaas" — pre-configured HTTP client
```

**Scenario A: Local execution**

The tool runs logic directly in the strategy. No external calls.

```java
@Component
public class EchoStrategy implements ToolExecutionStrategy {

    @Override
    public String getType() { return "echo"; }

    @Override
    public String execute(String toolInput, String executorConfig) {
        return "Echo: " + toolInput;
    }
}
```

DB row: `executor_type = 'echo'`, `executor_config = '{}'`

**Scenario B: Generic HTTP execution**

A single strategy serves many tools. Each tool's `executor_config` specifies the URL, method, headers, and body template.

```java
@Component
public class HttpStrategy implements ToolExecutionStrategy {

    @Override
    public String getType() { return "http"; }

    @Override
    public String execute(String toolInput, String executorConfig) {
        JsonNode config = objectMapper.readTree(executorConfig);
        String url = resolveUrl(config.get("url").asText(), input);
        String method = config.get("method").asText();
        // make the call
    }
}
```

DB rows:

```sql
-- GET with URL path substitution
INSERT INTO tool_definitions (tool_name, ..., executor_type, executor_config)
VALUES ('get_github_user', ..., 'http',
        '{"url":"https://api.github.com/users/{username}","method":"GET"}');

-- POST with body template
VALUES ('create_pastebin', ..., 'http',
        '{"url":"https://jsonplaceholder.typicode.com/posts","method":"POST",
          "bodyTemplate":{"title":"{title}","body":"{content}"}}');
```

**Scenario C: Pre-configured HTTP client**

A strategy with a shared base URL, auth headers, and timeouts baked into a Spring-managed `RestClient`. Tools only specify the path.

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
        JsonNode config = objectMapper.readTree(executorConfig);
        String path = config.get("path").asText();
        // call restClient.get().uri(path)...
    }
}
```

DB row: `executor_type = 'http_idaas'`, `executor_config = '{"path":"/users/{id}","method":"GET"}'`

## Registered Tools

### Static (code-defined)

| Tool | Source | Description |
|---|---|---|
| `calculate` | Library showcase | Basic arithmetic |
| `analyze_text` | This app | Text statistics |
| `transform_text` | This app | Text transformations |
| `get_current_time` | This app | Current time in a timezone |
| `ping` | This app | Simple ping/pong |
| `today_in_history` | This app | Wikipedia "on this day" API |

### Dynamic (from database)

| Tool | Executor | Description |
|---|---|---|
| `echo_message` | `echo` | Echo with prefix |
| `lookup_user` | `echo` | User lookup (echo) |
| `greet_user` | `echo` | Greeting generator (echo) |
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
    ├── execution/
    │   ├── EchoStrategy.java
    │   ├── HttpStrategy.java
    │   └── IdaasHttpStrategy.java
    ├── source/
    │   └── CustomToolDefinitionSource.java
    └── tools/
        ├── HostTools.java
        ├── TextTools.java
        ├── TimeTools.java
        └── WikipediaTodayTools.java
```
