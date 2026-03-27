# Spring Pluggable MCP

A Spring Boot library that turns any Spring application into an MCP (Model Context Protocol) server with support for **static tools** (code-defined) and **dynamic tools** (loaded at runtime from external sources).

Built with Spring Boot 3.5, Spring AI 1.1.4, and the MCP Streamable HTTP transport.

## What This Is

This is a **library**, not a standalone application. You add it as a dependency to your Spring Boot project (the "client"), and it gives you:

- An MCP server endpoint at `/mcp` (Streamable HTTP)
- Auto-discovery of `@Tool`-annotated beans from your application
- A pluggable system for loading and executing dynamic tools from a database or any other source
- Every extension point follows the same pattern: **interface + default implementation + `@ConditionalOnMissingBean`**

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
@SpringBootApplication
@ComponentScan(basePackages = {"your.app.package", "com.mcp.springpluggablemcp"})
public class YourApplication { }
```

## Extension Points

Every component is replaceable. The library provides sensible defaults, but your application can override any of them by providing your own bean.

| Extension Point | Interface | Default | What It Does |
|---|---|---|---|
| Tool loading source | `ToolDefinitionSource` | `SimpleQueryToolSource` | Where dynamic tool definitions come from |
| Row-to-record mapping | `ToolRecordMapper<T>` | `DefaultToolRecordMapper` (JDBC) | How raw source data maps to `DynamicToolRecord` |
| Strategy resolution | `ToolExecutionStrategyRegistry` | `DefaultToolExecutionStrategyRegistry` | How `executorType` maps to a strategy bean |
| Tool execution | `ToolExecutionStrategy` | *(you provide these)* | What happens when a tool is called |
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
dynamic-tools:
  refresh:
    enabled: false       # set true to poll for changes
    interval: 5m         # polling interval
```

These are the only properties the library requires. Everything else depends on which `ToolDefinitionSource` you use.

### Configuration (JDBC Defaults)

If you don't provide a custom `ToolDefinitionSource`, the library uses `SimpleQueryToolSource` which reads from a database:

```yaml
dynamic-tools:
  jdbc:
    datasource:
      url: jdbc:postgresql://localhost:5432/mcp_tools
      username: postgres
      password: postgres
      driver-class-name: org.postgresql.Driver
    query: "SELECT name, description, schema, type, config FROM tools WHERE enabled = true"
    column-mapping:
      name: name
      description: description
      input-schema: schema
      executor-type: type
      executor-config: config
    delta-query: "SELECT ... WHERE updated_at > ?"
```

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
├── config/
│   └── McpToolConfig.java                    # Auto-discovers @Tool beans
├── controller/
│   └── CalculatorTools.java                  # Showcase @Tool
├── service/
│   └── CalculatorService.java                # Showcase service
└── dynamic/
    ├── config/
    │   ├── DynamicToolProperties.java        # Core properties (refresh)
    │   ├── DynamicToolJdbcProperties.java    # JDBC-specific properties
    │   ├── DynamicToolDatasourceConfig.java  # Core beans (loader, registry)
    │   └── DynamicToolJdbcConfig.java        # JDBC default beans
    ├── loader/
    │   ├── DynamicToolLoader.java            # Interface
    │   ├── DefaultDynamicToolLoader.java     # Default: load on startup + refresh
    │   ├── ToolDefinitionSource.java         # Interface
    │   └── SimpleQueryToolSource.java        # Default: SQL query
    ├── mapping/
    │   ├── DynamicToolRecord.java            # Tool data model
    │   ├── ToolRecordMapper.java             # Interface (generic)
    │   └── DefaultToolRecordMapper.java      # Default: JDBC ResultSet
    └── execution/
        ├── ToolExecutionStrategy.java        # Interface
        ├── ToolExecutionStrategyRegistry.java          # Interface
        ├── DefaultToolExecutionStrategyRegistry.java   # Default: auto-collect beans
        └── DynamicToolCallback.java          # Bridges strategy to MCP ToolCallback
```
