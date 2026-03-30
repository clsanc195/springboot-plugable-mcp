package com.mcp.springpluggablemcp.config;

import com.mcp.springpluggablemcp.dynamic.loader.DynamicToolStatus;
import com.mcp.springpluggablemcp.tool.McpTool;
import com.mcp.springpluggablemcp.tool.McpToolUtils;
import com.mcp.springpluggablemcp.tool.MethodToolScanner;
import com.mcp.springpluggablemcp.tool.ToolCallback;
import io.modelcontextprotocol.server.McpSyncServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Discovers beans with {@link McpTool}-annotated methods and registers
 * them directly with the MCP SDK server on startup.
 * <p>
 * Replaces Spring AI's {@code MethodToolCallbackProvider} with our own
 * reflection-based {@link MethodToolScanner}.
 */
@Configuration
public class McpToolConfig {

    private static final Logger log = LoggerFactory.getLogger(McpToolConfig.class);

    private final ConfigurableApplicationContext context;
    private final McpSyncServer mcpSyncServer;
    private final DynamicToolStatus status;

    public McpToolConfig(ConfigurableApplicationContext context,
                         McpSyncServer mcpSyncServer,
                         DynamicToolStatus status) {
        this.context = context;
        this.mcpSyncServer = mcpSyncServer;
        this.status = status;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerStaticTools() {
        List<Object> toolBeans = new ArrayList<>();

        for (String beanName : context.getBeanDefinitionNames()) {
            Class<?> beanType = resolveBeanClass(beanName);
            if (beanType != null && hasToolMethods(beanType)) {
                toolBeans.add(context.getBean(beanName));
            }
        }

        if (toolBeans.isEmpty()) {
            log.info("No @McpTool beans found");
            return;
        }

        List<ToolCallback> callbacks = MethodToolScanner.scan(toolBeans.toArray());
        for (ToolCallback callback : callbacks) {
            var spec = McpToolUtils.toSyncToolSpecification(callback);
            mcpSyncServer.addTool(spec);
            status.toolRegistered(
                    callback.getToolDefinition().name(),
                    "@McpTool",
                    callback.getToolDefinition().name());
            log.info("Registered static tool: {}", callback.getToolDefinition().name());
        }

        log.info("Registered {} static @McpTool methods", callbacks.size());
    }

    private Class<?> resolveBeanClass(String beanName) {
        try {
            String className = context.getBeanFactory()
                    .getBeanDefinition(beanName)
                    .getBeanClassName();
            if (className != null) {
                return Class.forName(className);
            }
            return null;
        } catch (ClassNotFoundException e) {
            log.warn("Could not resolve class for bean '{}': {}", beanName, e.getMessage());
            return null;
        } catch (Exception e) {
            log.debug("Skipping bean '{}' during @McpTool scan: {}", beanName, e.getMessage());
            return null;
        }
    }

    private boolean hasToolMethods(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(McpTool.class)) {
                return true;
            }
        }
        return false;
    }
}
