package com.mcp.springpluggablemcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SpringPluggableMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringPluggableMcpApplication.class, args);
    }
}
