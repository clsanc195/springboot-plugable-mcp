# Spring Pluggable MCP (SDK Only)

A Spring Boot library that turns any Spring application into an MCP (Model Context Protocol) server with support for static tools (code-defined) and dynamic tools (loaded at runtime from any source).

Built with Spring Boot 3.5 and the MCP Java SDK 0.17.0 (Streamable HTTP transport). **No Spring AI dependency** — the library provides its own tool annotations, callback interfaces, reflection scanner, and MCP server configuration.

## What This Is

This is a **library**, not a standalone application. You add it as a dependency to your Spring Boot project, and it gives you:

- An MCP server endpoint at `/mcp` (Streamable HTTP) — configured automatically via `McpServerConfig`
- Auto-discovery of `@McpTool`-annotated beans from your application via a reflection-based `MethodToolScanner`
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

The library brings in the MCP Java SDK and Spring Web transitively. No additional MCP or Spring AI dependencies are needed.

Your application needs a component scan that includes the library's package:

```java
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@ComponentScan(basePackages = {"your.app.package", "com.mcp.springpluggablemcp"})
public class YourApplication { }
```

Exclude `DataSourceAutoConfiguration` since the library manages its own named datasources.

## Custom Tool Abstractions

This library replaces Spring AI's tool system with its own lightweight equivalents:

| This Library | Replaces (Spring AI) | Purpose |
|---|---|---|
| `@McpTool` | `@Tool` | Marks a method as an MCP tool |
| `@McpToolParam` | `@ToolParam` | Describes a tool parameter |
| `ToolCallback` | `o.s.ai.tool.ToolCallback` | Callable tool with definition + execution |
| `ToolDefinition` | `o.s.ai.tool.definition.ToolDefinition` | Tool name, description, input schema |
| `MethodToolScanner` | `MethodToolCallbackProvider` | Reflection-based scanner for `@McpTool` methods |
| `McpToolUtils` | `o.s.ai.mcp.McpToolUtils` | Converts `ToolCallback` to MCP SDK `SyncToolSpecification` |
| `McpServerConfig` | Spring AI auto-configuration | Creates `McpSyncServer`, transport, and router beans |

All custom types live in `com.mcp.springpluggablemcp.tool`.

## Extension Points

| Extension Point | Interface | Default | What It Does |
|---|---|---|---|
| Tool loading source | `ToolDefinitionSource` | *(client provides)* | Where dynamic tool definitions come from |
| Strategy resolution | `ToolExecutionStrategyRegistry` | `DefaultToolExecutionStrategyRegistry` | How `executorType` maps to a strategy bean |
| Tool execution | `ToolExecutionStrategy` | *(client provides)* | What happens when a tool is called |
| Loading lifecycle | `DynamicToolLoader` | `DefaultDynamicToolLoader` | When and how tools are loaded and refreshed |
| Static tool discovery | `MethodToolScanner` | Auto-scans all `@McpTool` beans | Which `@McpTool` methods become MCP tools |

## Static Tools

Any `@Component` with `@McpTool`-annotated methods is automatically discovered and registered as an MCP tool:

```java
@Component
public class MyTools {

    @McpTool(name = "greet", description = "Generate a greeting")
    public String greet(@McpToolParam(description = "Name") String name) {
        return "Hello, " + name;
    }
}
```

**Note:** compile with `-parameters` (Maven compiler flag) so that real parameter names are available at runtime.

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
  server:
    name: my-mcp-server
    version: 1.0.0
    mcp-endpoint: /mcp
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

Each datasource entry registers a named `DataSource` and `JdbcTemplate` bean:

| Bean Name | Type | Injected Via |
|---|---|---|
| `primary-dataSource` | `DataSource` | `@Qualifier("primary-dataSource")` |
| `primary-jdbcTemplate` | `JdbcTemplate` | `@Qualifier("primary-jdbcTemplate")` |
| `secondary-dataSource` | `DataSource` | `@Qualifier("secondary-dataSource")` |
| `secondary-jdbcTemplate` | `JdbcTemplate` | `@Qualifier("secondary-jdbcTemplate")` |

A global refresh fallback is available for sources that don't override `refreshInterval()`:

```yaml
spring-pluggable-mcp:
  refresh:
    enabled: true
    interval: 5m
```

## MCP Server Configuration

The library automatically creates and configures the MCP server via `McpServerConfig`:

- `WebMvcStreamableServerTransportProvider` — HTTP transport at the configured endpoint
- `McpSyncServer` — with `immediateExecution(true)` for synchronous servlet handling, and full capabilities (tools, resources, prompts, completions, logging)
- `RouterFunction` — exposes the `/mcp` endpoint via Spring WebMVC

All beans use `@ConditionalOnMissingBean`, so clients can provide their own.

Server identity is configured via YAML:

```yaml
spring-pluggable-mcp:
  server:
    name: my-mcp-server
    version: 2.0.0
    mcp-endpoint: /mcp
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

Source states: `healthy`, `retrying`, `gave_up`.

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
│   ├── McpServerConfig.java                  # MCP server + transport + router beans
│   └── McpToolConfig.java                    # Auto-discovers @McpTool beans
├── tool/
│   ├── McpTool.java                          # @McpTool annotation
│   ├── McpToolParam.java                     # @McpToolParam annotation
│   ├── ToolCallback.java                     # Callable tool interface
│   ├── ToolDefinition.java                   # Tool metadata record
│   ├── McpToolUtils.java                     # Converts ToolCallback → MCP SDK spec
│   └── MethodToolScanner.java                # Reflection scanner for @McpTool methods
└── dynamic/
    ├── config/
    │   ├── DynamicToolProperties.java        # Properties (server + refresh + datasources)
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
        └── DynamicToolCallback.java          # Bridges strategy to ToolCallback
```
