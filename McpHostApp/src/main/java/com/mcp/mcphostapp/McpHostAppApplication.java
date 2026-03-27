package com.mcp.mcphostapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.mcp.mcphostapp", "com.mcp.springpluggablemcp"})
public class McpHostAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpHostAppApplication.class, args);
    }
}
