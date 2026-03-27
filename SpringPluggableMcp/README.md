# Spring Pluggable MCP

A Spring Boot library that turns any Spring application into an MCP (Model Context Protocol) server with support for **static tools** (code-defined) and **dynamic tools** (loaded at runtime from external sources).

Built with Spring Boot 3.5, Spring AI 1.1.4, and the MCP Streamable HTTP transport.

## What This Is

This is a **library**, not a standalone application. You add it as a dependency to your Spring Boot project (the "client"), and it gives you:

- An MCP server endpoint at `/mcp` (Streamable HTTP)
- Auto-discovery of `@Tool`-annotated beans from your application
- A pluggable system for loading and executing dynamic tools from any source
- Named JDBC datasource/JdbcTemplate bean registration from YAML configuration
- Every extension point follows the same pattern: **interface + default implementation + `@ConditionalOnMissingBean`**

The library does **not** provide any `ToolDefinitionSource` implementations. Clients own all source implementations and inject the named JDBC beans they need.

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

Note: exclude `DataSourceAutoConfiguration` since the library manages its own named datasources via `DynamicToolJdbcConfig`.

## Extension Points

Every component is replaceable. The library provides sensible defaults, but your application can override any of them by providing your own bean.

| Extension Point | Interface | Default | What It Does |
|---|---|---|---|
| Tool loading source | `ToolDefinitionSource` | *(client provides these)* | Where dynamic tool definitions come from |
| Strategy resolution | `ToolExecutionStrategyRegistry` | `DefaultToolExecutionStrategyRegistry` | How `executorType` maps to a strategy bean |
| Tool execution | `ToolExecutionStrategy` | *(client provides these)* | What happens when a tool is called |
| Loading lifecycle | `DynamicToolLoader` | `DefaultDynamicToolLoader` | When and how tools are loaded and refreshed |
| Static tool discovery | `MethodToolCallbackProvider` | Auto-scans all `@Tool` beans | Which `@Tool` methods become MCP tools |

## Static Tools

Any `@Component` with `@Tool`-annotated methods is automatically discovered and registered as an MCP tool. Works for beans in your application or in the library.

```java
@Component
public class MyTools {

    @Tool(name = "greet", description = "Generate a greeting")
    public String greet(@ToolParam(description = "Name") String name) {
        return "Hello, " + name;
    }
}
```

No registration code needed — the library finds it.

## Dynamic Tools

Dynamic tools are defined outside your code (typically in a database) and loaded at runtime. Each tool definition includes:

- `name`, `description`, `inputSchema` — exposed to MCP clients
- `executorType` — routes to a `ToolExecutionStrategy` bean
- `executorConfig` — per-tool configuration passed to the strategy at execution time

### Configuration (Core)

```yaml
spring-pluggable-mcp:
  refresh:
    enabled: false       # set true to poll for changes
    interval: 5m         # polling interval
```

These are the only properties the library requires for the core loader. Everything else depends on which sources you implement.

### Configuration (JDBC Named Sources)

The library can register named `DataSource` and `JdbcTemplate` beans from YAML. Each entry in `spring-pluggable-mcp.datasources` becomes a pair of beans that clients inject by qualifier. The library does **not** create `ToolDefinitionSource` beans from this configuration — clients write their own source implementations.

```yaml
spring-pluggable-mcp:
  datasources:
    - name: primary
      url: jdbc:postgresql://localhost:5432/mcp_tools
      username: postgres
      password: postgres
      driver-class-name: org.postgresql.Driver
    - name: secondary
      url: jdbc:postgresql://localhost:5433/other_db
      username: postgres
      password: postgres
      driver-class-name: org.postgresql.Driver
```

This registers the following beans:

| Bean Name | Type | Injected Via |
|---|---|---|
| `primary-dataSource` | `DataSource` | `@Qualifier("primary-dataSource")` |
| `primary-jdbcTemplate` | `JdbcTemplate` | `@Qualifier("primary-jdbcTemplate")` |
| `secondary-dataSource` | `DataSource` | `@Qualifier("secondary-dataSource")` |
| `secondary-jdbcTemplate` | `JdbcTemplate` | `@Qualifier("secondary-jdbcTemplate")` |

Client source example:

```java
@Component
public class MyJdbcToolSource implements ToolDefinitionSource {

    private final JdbcTemplate jdbcTemplate;

    public MyJdbcToolSource(@Qualifier("primary-jdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<DynamicToolRecord> loadAll() {
        return jdbcTemplate.query("SELECT ...",
                (rs, rowNum) -> new DynamicToolRecord(
                        rs.getString("tool_name"),
                        rs.getString("tool_description"),
                        rs.getString("input_schema"),
                        rs.getString("executor_type"),
                        rs.getString("executor_config")
                ));
    }
}
```

## Overriding Library Defaults

The library ships an `application.yml` with MCP server identity defaults (`name: spring-pluggable-mcp`). Your client application's `application.yml` takes precedence — simply define your own:

```yaml
spring:
  ai:
    mcp:
      server:
        name: my-mcp-server
        version: 2.0.0
```

The library's values are only used if your application doesn't set them.

## Multiple Tool Definition Sources

The loader accepts `List<ToolDefinitionSource>` — all `@Component` beans implementing the interface are collected automatically. Each source loads independently and failures in one source don't affect others.

```java
@Component
public class RestApiToolSource implements ToolDefinitionSource {
    @Override
    public List<DynamicToolRecord> loadAll() { /* fetch from API */ }
}

@Component
public class FileToolSource implements ToolDefinitionSource {
    @Override
    public List<DynamicToolRecord> loadAll() { /* read from YAML file */ }
}
```

Both sources contribute tools to the same MCP server. If a source fails at startup, it retries automatically on the next refresh cycle (up to 10 attempts with logging on each retry).

## Resilience

- **Source failures at startup** are logged and retried on the next refresh cycle
- **Individual tool registration failures** are isolated — one bad tool doesn't block others
- **Retry limit** — sources that fail repeatedly give up after 10 attempts to avoid log spam
- **Tool name collisions** across sources are logged with a warning identifying both sources before overriding
- **Thread safety** — tool registration uses a `ReentrantLock` to prevent race conditions during concurrent refresh and MCP request handling
- **McpToolConfig logging** — bean resolution errors during `@Tool` scanning are logged as warnings instead of silently swallowed

## Showcase: CalculatorTools

The library ships one sample `@Tool` bean as a reference:

```java
@Component
public class CalculatorTools {

    @Tool(name = "calculate", description = "Perform basic arithmetic")
    public CalculationResult calculate(
            @ToolParam(description = "First operand") double a,
            @ToolParam(description = "Second operand") double b,
            @ToolParam(description = "Operator: add, subtract, multiply, divide") String operator) {
        return calculatorService.calculate(a, b, operator);
    }
}
```

## Project Structure

```
src/main/java/com/mcp/springpluggablemcp/
├── SpringPluggableMcpApplication.java
├── config/
│   └── McpToolConfig.java                    # Auto-discovers @Tool beans
├── controller/
│   └── CalculatorTools.java                  # Showcase @Tool
├── service/
│   └── CalculatorService.java                # Showcase service
└── dynamic/
    ├── config/
    │   ├── DynamicToolProperties.java        # Core properties (refresh + datasources)
    │   ├── DynamicToolDatasourceConfig.java  # Core beans (loader, registry)
    │   └── DynamicToolJdbcConfig.java        # Registers named DataSource/JdbcTemplate beans
    ├── loader/
    │   ├── DynamicToolLoader.java            # Interface
    │   ├── DefaultDynamicToolLoader.java     # Default: load on startup + refresh
    │   └── ToolDefinitionSource.java         # Interface (client-implemented)
    ├── mapping/
    │   └── DynamicToolRecord.java            # Tool data model
    └── execution/
        ├── ToolExecutionStrategy.java        # Interface
        ├── ToolExecutionStrategyRegistry.java          # Interface
        ├── DefaultToolExecutionStrategyRegistry.java   # Default: auto-collect beans
        └── DynamicToolCallback.java          # Bridges strategy to MCP ToolCallback
```
