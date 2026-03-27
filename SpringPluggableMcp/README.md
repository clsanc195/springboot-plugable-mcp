# Spring Pluggable MCP

A Spring Boot library that turns any Spring application into an MCP (Model Context Protocol) server with support for static tools (code-defined) and dynamic tools (loaded at runtime from any source).

Built with Spring Boot 3.5, Spring AI 1.1.4, and the MCP Streamable HTTP transport.

## What This Is

This is a **library**, not a standalone application. You add it as a dependency to your Spring Boot project, and it gives you:

- An MCP server endpoint at `/mcp` (Streamable HTTP)
- Auto-discovery of `@Tool`-annotated beans from your application
- A pluggable system for loading and executing dynamic tools from any source
- Named JDBC datasource/JdbcTemplate bean registration from YAML
- An Actuator endpoint at `/actuator/mcptools` showing all registered tools and source health
- Every extension point follows the same pattern: **interface + default implementation + `@ConditionalOnMissingBean`**

The library does **not** provide any `ToolDefinitionSource` or `ToolExecutionStrategy` implementations. Clients own all source and execution logic.

## Adding the Dependency

```xml
<dependency>
    <groupId>com.mcp</groupId>
    <artifactId>spring-pluggable-mcp</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

Your application also needs:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

And a component scan that includes the library's package:

```java
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@ComponentScan(basePackages = {"your.app.package", "com.mcp.springpluggablemcp"})
public class YourApplication { }
```

Exclude `DataSourceAutoConfiguration` since the library manages its own named datasources.

## Extension Points

| Extension Point | Interface | Default | What It Does |
|---|---|---|---|
| Tool loading source | `ToolDefinitionSource` | *(client provides)* | Where dynamic tool definitions come from |
| Strategy resolution | `ToolExecutionStrategyRegistry` | `DefaultToolExecutionStrategyRegistry` | How `executorType` maps to a strategy bean |
| Tool execution | `ToolExecutionStrategy` | *(client provides)* | What happens when a tool is called |
| Loading lifecycle | `DynamicToolLoader` | `DefaultDynamicToolLoader` | When and how tools are loaded and refreshed |
| Static tool discovery | `MethodToolCallbackProvider` | Auto-scans all `@Tool` beans | Which `@Tool` methods become MCP tools |

## Static Tools

Any `@Component` with `@Tool`-annotated methods is automatically discovered and registered as an MCP tool:

```java
@Component
public class MyTools {

    @Tool(name = "greet", description = "Generate a greeting")
    public String greet(@ToolParam(description = "Name") String name) {
        return "Hello, " + name;
    }
}
```

## Dynamic Tools

Dynamic tools are defined outside your code and loaded at runtime. Each tool is a `DynamicToolRecord` with:

- `name`, `description`, `inputSchema` -- exposed to MCP clients
- `executorType` -- routes to a `ToolExecutionStrategy` bean
- `executorConfig` -- per-tool configuration passed to the strategy

### ToolDefinitionSource

Implement this interface as a `@Component` to load tools from any source. Multiple sources can coexist -- the loader calls each one independently.

Each source controls its own behavior via default method overrides:

| Method | Default | Purpose |
|---|---|---|
| `loadAll()` | *(required)* | Load all tool definitions |
| `loadSince(Instant)` | empty list | Incremental refresh (opt-in) |
| `refreshInterval()` | `null` (no refresh) | How often to reload |
| `sourceTimeout()` | 30 seconds | Max wait for `loadAll`/`loadSince` before cancelling |
| `maxRetryAttempts()` | 10 | How many times to retry if initial load fails |

Example:

```java
@Component
public class MyToolSource implements ToolDefinitionSource {

    @Override
    public List<DynamicToolRecord> loadAll() {
        // load from database, REST API, file, etc.
    }

    @Override
    public Duration refreshInterval() {
        return Duration.ofMinutes(5);
    }

    @Override
    public Duration sourceTimeout() {
        return Duration.ofSeconds(15);
    }
}
```

### Configuration

```yaml
spring-pluggable-mcp:
  datasources:
    - name: primary
      url: jdbc:postgresql://localhost:5432/my_db
      username: postgres
      password: postgres
      driver-class-name: org.postgresql.Driver
    - name: secondary
      url: jdbc:postgresql://localhost:5433/other_db
      username: postgres
      password: postgres
      driver-class-name: org.postgresql.Driver
```

Each entry registers a named `DataSource` and `JdbcTemplate` bean:

| Bean Name | Type | Injected Via |
|---|---|---|
| `primary-dataSource` | `DataSource` | `@Qualifier("primary-dataSource")` |
| `primary-jdbcTemplate` | `JdbcTemplate` | `@Qualifier("primary-jdbcTemplate")` |
| `secondary-dataSource` | `DataSource` | `@Qualifier("secondary-dataSource")` |
| `secondary-jdbcTemplate` | `JdbcTemplate` | `@Qualifier("secondary-jdbcTemplate")` |

The library does **not** create `ToolDefinitionSource` beans from this configuration. Clients inject the named `JdbcTemplate` and write their own source logic:

```java
@Component
public class MyJdbcToolSource implements ToolDefinitionSource {

    public MyJdbcToolSource(@Qualifier("primary-jdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<DynamicToolRecord> loadAll() {
        return jdbcTemplate.query("SELECT ...",
                (rs, rowNum) -> new DynamicToolRecord( ... ));
    }
}
```

A global refresh fallback is available for sources that don't override `refreshInterval()`:

```yaml
spring-pluggable-mcp:
  refresh:
    enabled: true
    interval: 5m
```

## Actuator Endpoint

The library provides an endpoint at `/actuator/mcptools` that shows all registered tools (static and dynamic) and the health of each source.

Enable it in your application:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,mcptools
```

Response:

```json
{
  "totalTools": 16,
  "tools": [
    { "name": "ping", "executorType": "@Tool", "source": "HostTools", "registeredAt": "..." },
    { "name": "echo_message", "executorType": "echo", "source": "JdbcToolDefinitionSource", "registeredAt": "..." }
  ],
  "sources": {
    "JdbcToolDefinitionSource": { "state": "healthy", "toolCount": 8, "retryAttempts": 0 },
    "InMemoryToolDefinitionSource": { "state": "healthy", "toolCount": 2, "retryAttempts": 0 }
  }
}
```

Source states: `healthy`, `retrying`, `gave_up`.

## Overriding Library Defaults

The library ships an `application.yml` with MCP server identity defaults. Your client's `application.yml` takes precedence:

```yaml
spring:
  ai:
    mcp:
      server:
        name: my-mcp-server
        version: 2.0.0
```

## Resilience

- **Source failures at startup** are logged and retried on the next refresh cycle
- **Individual tool registration failures** are isolated -- one bad tool doesn't block others
- **Retry limit** -- configurable per source via `maxRetryAttempts()`, defaults to 10
- **Source timeout** -- configurable per source via `sourceTimeout()`, defaults to 30s. Hanging sources are cancelled without blocking others
- **Tool name collisions** across sources are logged with a warning identifying both sources
- **Thread safety** -- tool registration uses a `ReentrantLock` to prevent race conditions
- **Clean shutdown** -- the loader's `ExecutorService` is shut down via `DisposableBean` on application stop

## Project Structure

```
src/main/java/com/mcp/springpluggablemcp/
├── SpringPluggableMcpApplication.java
├── config/
│   └── McpToolConfig.java                    # Auto-discovers @Tool beans
└── dynamic/
    ├── config/
    │   ├── DynamicToolProperties.java        # Properties (refresh + datasources)
    │   ├── DynamicToolDatasourceConfig.java  # Core beans (loader, registry, status)
    │   ├── DynamicToolJdbcConfig.java        # Registers named DataSource/JdbcTemplate beans
    │   └── McpToolsEndpoint.java             # Actuator endpoint at /actuator/mcptools
    ├── loader/
    │   ├── DynamicToolLoader.java            # Interface
    │   ├── DefaultDynamicToolLoader.java     # Default: load on startup + per-source refresh
    │   ├── ToolDefinitionSource.java         # Interface (client implements)
    │   └── DynamicToolStatus.java            # Tracks tools and source health
    ├── mapping/
    │   └── DynamicToolRecord.java            # Tool data model
    └── execution/
        ├── ToolExecutionStrategy.java        # Interface (client implements)
        ├── ToolExecutionStrategyRegistry.java          # Interface
        ├── DefaultToolExecutionStrategyRegistry.java   # Default: auto-collect beans
        └── DynamicToolCallback.java          # Bridges strategy to MCP ToolCallback
```
