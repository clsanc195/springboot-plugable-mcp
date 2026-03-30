package com.mcp.springpluggablemcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.springpluggablemcp.dynamic.config.DynamicToolProperties;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Manually configures the MCP server and transport layer using the
 * MCP Java SDK directly — no Spring AI auto-configuration needed.
 * <p>
 * Creates three beans:
 * <ol>
 *   <li>{@link WebMvcStreamableServerTransportProvider} — HTTP transport</li>
 *   <li>{@link McpSyncServer} — the MCP server instance</li>
 *   <li>{@link RouterFunction} — exposes the MCP endpoint via Spring WebMVC</li>
 * </ol>
 * All beans use {@code @ConditionalOnMissingBean} so consumers can
 * provide their own if needed.
 */
@Configuration
public class McpServerConfig {

    @Bean
    @ConditionalOnMissingBean
    public WebMvcStreamableServerTransportProvider mcpTransportProvider(ObjectMapper objectMapper,
                                                                       DynamicToolProperties properties) {
        String endpoint = "/mcp";
        if (properties.server() != null && properties.server().mcpEndpoint() != null) {
            endpoint = properties.server().mcpEndpoint();
        }

        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                .mcpEndpoint(endpoint)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public McpSyncServer mcpSyncServer(WebMvcStreamableServerTransportProvider transportProvider,
                                       DynamicToolProperties properties) {
        String name = "mcp-server";
        String version = "1.0.0";

        if (properties.server() != null) {
            if (properties.server().name() != null) name = properties.server().name();
            if (properties.server().version() != null) version = properties.server().version();
        }

        return McpServer.sync(transportProvider)
                .serverInfo(name, version)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .completions()
                        .logging()
                        .prompts(true)
                        .resources(false, true)
                        .tools(true)
                        .build())
                .immediateExecution(true)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(name = "mcpRouterFunction")
    public RouterFunction<ServerResponse> mcpRouterFunction(
            WebMvcStreamableServerTransportProvider transportProvider) {
        return transportProvider.getRouterFunction();
    }
}
