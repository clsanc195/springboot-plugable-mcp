# MCP Host App (SDK Only)

A reference client application that demonstrates how to use the [SpringPluggableMcp](../SpringPluggableMcp) library to build an MCP server with both static and dynamic tools. **No Spring AI dependency** — uses the MCP Java SDK directly via the library's custom abstractions.

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
| `mcp-server` | 8790 | This application |
| `mcp-inspector` | 6274 | MCP Inspector UI |

Connect the inspector at `http://localhost:6274` using transport **Streamable HTTP** and URL `http://mcp-server:8790/mcp`.

### Locally

```bash
mvn spring-boot:run
```

Requires PostgreSQL -- use `docker compose up -d postgres` to start just the database.

### Endpoints

| URL | Description |
|---|---|
| `http://localhost:8790/mcp` | MCP server |
| `http://localhost:8790/actuator/mcptools` | Tool dashboard |
| `http://localhost:8790/health` | Health check |

### Database

```
URL:      jdbc:postgresql://localhost:5432/mcp_tools
User:     postgres
Password: postgres
```

## How It All Comes Together

There are two ways to expose tools through the MCP server:

1. **Static tools** -- `@McpTool` annotated methods in `@Component` beans. Auto-discovered via `MethodToolScanner`. Execution logic lives in the method.
2. **Dynamic tools** -- loaded from `ToolDefinitionSource` beans at runtime. Each record has an `executorType` that routes to a `ToolExecutionStrategy` bean, and an `executorConfig` with per-tool configuration.

## 1. Static Tools

Any `@Component` with `@McpTool` methods is auto-discovered by the library.

```
mcp/tools/
├── HostTools.java              # ping
├── TextTools.java              # analyze_text, transform_text
├── TimeTools.java              # get_current_time
├── WikipediaTodayTools.java    # today_in_history (delegates to WikipediaClient)
└── wikipedia/
    └── WikipediaClient.java    # Wikipedia API client (RestClient, response parsing)
```

Example -- a tool that delegates to an external API client:

```java
@Component
public class WikipediaTodayTools {

    private final WikipediaClient wikipediaClient;

    public WikipediaTodayTools(WikipediaClient wikipediaClient) {
        this.wikipediaClient = wikipediaClient;
    }

    @McpTool(name = "today_in_history",
          description = "Get historical events for a given date from Wikipedia")
    public String todayInHistory(
            @McpToolParam(description = "Month (1-12)", required = false) Integer month,
            @McpToolParam(description = "Day (1-31)", required = false) Integer day) {
        // delegates to WikipediaClient for HTTP call and parsing
    }
}
```

The tool definition stays clean and focused. HTTP concerns (RestClient setup, response parsing) live in `WikipediaClient`.

## 2. Tool Definition Sources

Sources tell the library where to find dynamic tool definitions. This app has four sources running side by side:

```
mcp/source/
├── JdbcToolDefinitionSource.java          # Loads from PostgreSQL via primary-jdbcTemplate
├── SecondaryJdbcToolDefinitionSource.java  # Loads from PostgreSQL via secondary-jdbcTemplate
├── InMemoryToolDefinitionSource.java       # Loads from a hardcoded list
└── HttpToolDefinitionSource.java           # Loads from a remote HTTP API
```

Each source independently configures its own refresh interval, timeout, and retry limit:

| Source | Refresh | Timeout | Max Retries |
|---|---|---|---|
| `JdbcToolDefinitionSource` | 5 min | 15s | 5 |
| `SecondaryJdbcToolDefinitionSource` | 10 min | 60s | 3 |
| `InMemoryToolDefinitionSource` | none | 30s (default) | 10 (default) |
| `HttpToolDefinitionSource` | 5 min | 15s | 3 |

### JdbcToolDefinitionSource

Injects the library's named `JdbcTemplate` via `@Qualifier("primary-jdbcTemplate")`. Uses a two-table schema with per-server tool assignment:

```java
@Component
public class JdbcToolDefinitionSource implements ToolDefinitionSource {

    public JdbcToolDefinitionSource(
            @Qualifier("primary-jdbcTemplate") JdbcTemplate jdbcTemplate,
            @Value("${spring-pluggable-mcp.server.name}") String serverId) { ... }

    @Override
    public Duration refreshInterval() { return Duration.ofMinutes(5); }

    @Override
    public Duration sourceTimeout() { return Duration.ofSeconds(15); }

    @Override
    public int maxRetryAttempts() { return 5; }

    @Override
    public List<DynamicToolRecord> loadAll() { ... }

    @Override
    public List<DynamicToolRecord> loadSince(Instant since) { ... }
}
```

### Configuration

The library registers named `DataSource` and `JdbcTemplate` beans from YAML. The client's sources inject these by qualifier:

```yaml
spring-pluggable-mcp:
  server:
    name: mcp-host-app
    version: 1.0.0
    mcp-endpoint: /mcp
  datasources:
    - name: primary
      url: ${PRIMARY_DB_URL:jdbc:postgresql://localhost:5432/mcp_tools}
      username: ${PRIMARY_DB_USERNAME:postgres}
      password: ${PRIMARY_DB_PASSWORD:postgres}
      driver-class-name: ${PRIMARY_DB_DRIVER:org.postgresql.Driver}
    - name: secondary
      url: ${SECONDARY_DB_URL:jdbc:postgresql://localhost:5432/mcp_tools}
      username: ${SECONDARY_DB_USERNAME:postgres}
      password: ${SECONDARY_DB_PASSWORD:postgres}
      driver-class-name: ${SECONDARY_DB_DRIVER:org.postgresql.Driver}
```

## 3. Execution Strategies

When a dynamic tool is called, the library resolves the `ToolExecutionStrategy` bean whose `getType()` matches the record's `executorType`, then calls `execute(toolInput, executorConfig)`.

```
mcp/execution/
├── EchoStrategy.java           # type: "echo"
├── HttpStrategy.java           # type: "http"
├── IdaasHttpStrategy.java      # type: "http_idaas"
└── ToolExecutionUtils.java     # Shared: placeholders, body templates, error JSON
```

### Pattern A: Local Execution

```java
@Component
public class EchoStrategy implements ToolExecutionStrategy {
    @Override public String getType() { return "echo"; }
    @Override public String execute(String toolInput, String executorConfig) {
        return "Echo: " + toolInput + " | Config: " + executorConfig;
    }
}
```

### Pattern B: Generic HTTP

A single strategy serves many tools. Each tool's `executorConfig` specifies URL, method, headers, body template:

| Tool | `executorConfig` |
|---|---|
| `get_github_user` | `{"url":"https://api.github.com/users/{username}","method":"GET"}` |
| `create_pastebin` | `{"url":"https://jsonplaceholder.typicode.com/posts","method":"POST","bodyTemplate":{"title":"{title}"}}` |

### Pattern C: Pre-configured HTTP Client

Shared base URL and auth baked into a `RestClient`. Tools only specify the path:

```java
@Component
public class IdaasHttpStrategy implements ToolExecutionStrategy {

    public IdaasHttpStrategy(
            @Value("${idaas.base-url}") String baseUrl,
            @Value("${idaas.api-key}") String apiKey, ...) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Api-Key", apiKey)
                .build();
    }

    @Override public String getType() { return "http_idaas"; }
}
```

## Application Bootstrap

```java
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@EnableScheduling
@ComponentScan(basePackages = {"com.mcp.mcphostapp", "com.mcp.springpluggablemcp"})
public class McpHostAppApplication { }
```

`DataSourceAutoConfiguration` is excluded because the library manages named datasources through `DynamicToolJdbcConfig`.

## Registered Tools

### Static (5 tools)

| Tool | Source | Description |
|---|---|---|
| `analyze_text` | TextTools | Text statistics |
| `transform_text` | TextTools | Text transformations |
| `get_current_time` | TimeTools | Current time in a timezone |
| `ping` | HostTools | Simple ping/pong |
| `today_in_history` | WikipediaTodayTools | Wikipedia "on this day" API |

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

### Dynamic from SecondaryJdbcToolDefinitionSource (1 tool)

| Tool | Executor | Description |
|---|---|---|
| `unrelated_tool` | `echo` | Loaded from secondary source |

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
    │   ├── IdaasHttpStrategy.java
    │   └── ToolExecutionUtils.java
    ├── source/
    │   ├── HttpToolDefinitionSource.java
    │   ├── InMemoryToolDefinitionSource.java
    │   ├── JdbcToolDefinitionSource.java
    │   └── SecondaryJdbcToolDefinitionSource.java
    └── tools/
        ├── HostTools.java
        ├── TextTools.java
        ├── TimeTools.java
        ├── WikipediaTodayTools.java
        └── wikipedia/
            └── WikipediaClient.java
```
