package com.mcp.mcphostapp.mcp.execution;

import com.mcp.springpluggablemcp.dynamic.execution.ToolExecutionStrategy;
import org.springframework.stereotype.Component;

@Component
public class EchoStrategy implements ToolExecutionStrategy {

    @Override
    public String getType() {
        return "echo";
    }

    @Override
    public String execute(String toolInput, String executorConfig) {
        return "Echo: " + toolInput + " | Config: " + executorConfig;
    }
}
