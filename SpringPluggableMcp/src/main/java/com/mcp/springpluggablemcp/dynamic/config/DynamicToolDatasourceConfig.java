package com.mcp.springpluggablemcp.dynamic.config;

import com.mcp.springpluggablemcp.dynamic.execution.DefaultToolExecutionStrategyRegistry;
import com.mcp.springpluggablemcp.dynamic.execution.ToolExecutionStrategy;
import com.mcp.springpluggablemcp.dynamic.execution.ToolExecutionStrategyRegistry;
import com.mcp.springpluggablemcp.dynamic.loader.DefaultDynamicToolLoader;
import com.mcp.springpluggablemcp.dynamic.loader.DynamicToolLoader;
import com.mcp.springpluggablemcp.dynamic.loader.ToolDefinitionSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.modelcontextprotocol.server.McpSyncServer;
import org.springframework.scheduling.TaskScheduler;

import java.util.List;

/**
 * Core configuration — beans that every dynamic tool setup needs
 * regardless of how tools are loaded.
 */
@Configuration
@EnableConfigurationProperties(DynamicToolProperties.class)
public class DynamicToolDatasourceConfig {

    @Bean
    @ConditionalOnMissingBean(ToolExecutionStrategyRegistry.class)
    public ToolExecutionStrategyRegistry defaultToolExecutionStrategyRegistry(
            List<ToolExecutionStrategy> strategies) {
        return new DefaultToolExecutionStrategyRegistry(strategies);
    }

    @Bean
    @ConditionalOnMissingBean(DynamicToolLoader.class)
    public DynamicToolLoader dynamicToolLoader(
            ToolDefinitionSource source,
            ToolExecutionStrategyRegistry strategyRegistry,
            McpSyncServer mcpSyncServer,
            DynamicToolProperties properties,
            TaskScheduler taskScheduler) {
        return new DefaultDynamicToolLoader(source, strategyRegistry, mcpSyncServer, properties, taskScheduler);
    }
}
