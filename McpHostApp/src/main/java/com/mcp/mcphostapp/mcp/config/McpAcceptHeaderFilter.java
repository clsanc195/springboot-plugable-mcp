package com.mcp.mcphostapp.mcp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Spring AI's Streamable HTTP transport requires Accept to contain both
 * application/json and text/event-stream. Some MCP clients (e.g., MCP Inspector)
 * only send text/event-stream. This filter normalizes the Accept header on the
 * /mcp endpoint so that valid MCP clients are not rejected with 406.
 */
@Configuration
public class McpAcceptHeaderFilter {

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> mcpAcceptFilter() {
        var registration = new FilterRegistrationBean<OncePerRequestFilter>();
        registration.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain)
                    throws ServletException, IOException {

                String accept = request.getHeader("Accept");
                if (accept != null && accept.contains("text/event-stream")
                        && !accept.contains("application/json")) {

                    filterChain.doFilter(new HttpServletRequestWrapper(request) {
                        @Override
                        public String getHeader(String name) {
                            if ("Accept".equalsIgnoreCase(name)) {
                                return "text/event-stream, application/json";
                            }
                            return super.getHeader(name);
                        }

                        @Override
                        public Enumeration<String> getHeaders(String name) {
                            if ("Accept".equalsIgnoreCase(name)) {
                                return Collections.enumeration(
                                        List.of("text/event-stream, application/json"));
                            }
                            return super.getHeaders(name);
                        }
                    }, response);
                } else {
                    filterChain.doFilter(request, response);
                }
            }
        });
        registration.addUrlPatterns("/mcp");
        registration.setOrder(1);
        return registration;
    }
}
