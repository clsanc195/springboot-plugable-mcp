package com.mcp.mcphostapp.mcp.source;

import com.mcp.springpluggablemcp.dynamic.loader.ToolDefinitionSource;
import com.mcp.springpluggablemcp.dynamic.mapping.DynamicToolRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Example: loading tool definitions from a remote HTTP API.
 * <p>
 * The client owns the full RestClient setup — authentication,
 * headers, interceptors, timeouts, mTLS, etc. are all configured
 * here, not in the library.
 */
@Component
public class HttpToolDefinitionSource implements ToolDefinitionSource {

    private static final Logger log = LoggerFactory.getLogger(HttpToolDefinitionSource.class);

    private final RestClient restClient;

    public HttpToolDefinitionSource() {
        this.restClient = RestClient.builder()
                .baseUrl("https://internal-api.example.com/mcp/tools")
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-API-Key", "my-secret-key")
                .build();
    }

    @Override
    public List<DynamicToolRecord> loadAll() {
        log.info("Loading tool definitions from remote API");

        return restClient.get()
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    @Override
    public List<DynamicToolRecord> loadSince(Instant since) {
        log.info("Loading delta tool definitions from remote API since {}", since);

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("since", since.toString())
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    @Override
    public Duration refreshInterval() {
        return Duration.ofMinutes(5);
    }

    @Override
    public Duration sourceTimeout() {
        return Duration.ofSeconds(15);
    }

    @Override
    public int maxRetryAttempts() {
        return 3;
    }
}
