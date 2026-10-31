package io.kestra.plugin.metabase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.http.client.configurations.TimeoutConfiguration;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import org.apache.commons.lang3.StringUtils;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * Reusable HTTP client wrapper for Metabase REST API.
 */
public class MetabaseClient implements Closeable {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    private final RunContext runContext;
    private final String baseUrl;
    private final String apiKey;
    private String sessionToken;
    private final String username;
    private final String password;
    private final HttpClient httpClient;

    public MetabaseClient(
        RunContext runContext,
        String baseUrl,
        String apiKey,
        String sessionToken,
        String username,
        String password,
        Duration timeout
    ) throws Exception {
        this.runContext = runContext;
        this.baseUrl = StringUtils.stripEnd(baseUrl, "/");
        this.apiKey = apiKey;
        this.sessionToken = sessionToken;
        this.username = username;
        this.password = password;

        HttpConfiguration.HttpConfigurationBuilder configBuilder = HttpConfiguration.builder();
        if (timeout != null) {
            configBuilder.timeout(TimeoutConfiguration.builder()
                .connectTimeout(Property.of(timeout))
                .readIdleTimeout(Property.of(timeout))
                .build());
        }

        this.httpClient = new HttpClient(runContext, configBuilder.build());

        // Initialize session if only credentials are provided
        if (StringUtils.isBlank(this.apiKey) && StringUtils.isBlank(this.sessionToken)) {
            if (StringUtils.isNotBlank(this.username) && StringUtils.isNotBlank(this.password)) {
                authenticateSession();
            }
        }
    }

    private void authenticateSession() throws Exception {
        URI sessionUri = buildUri("/api/session");
        String payload = MAPPER.writeValueAsString(Map.of(
            "username", this.username,
            "password", this.password
        ));

        HttpRequest request = HttpRequest.builder()
            .uri(sessionUri)
            .method("POST")
            .addHeader("Content-Type", "application/json")
            .body(HttpRequest.StringRequestBody.builder()
                .content(payload)
                .contentType("application/json")
                .build())
            .build();

        HttpResponse<String> response = this.httpClient.request(request, String.class);
        if (response.getStatus().getCode() >= 400) {
            throw new IllegalStateException("Metabase authentication failed with status: "
                + response.getStatus().getCode() + ", body: " + response.getBody());
        }

        Map<String, Object> sessionResponse = MAPPER.readValue(response.getBody(), JacksonMapper.MAP_TYPE_REFERENCE);
        Object id = sessionResponse.get("id");
        if (id == null) {
            throw new IllegalStateException("Metabase session response did not contain session id: " + response.getBody());
        }
        this.sessionToken = id.toString();
        runContext.logger().debug("Successfully authenticated session token with Metabase.");
    }

    private URI buildUri(String path) {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(this.baseUrl + normalizedPath);
    }

    private HttpRequest.HttpRequestBuilder baseRequestBuilder(String path, String method) {
        HttpRequest.HttpRequestBuilder builder = HttpRequest.builder()
            .uri(buildUri(path))
            .method(method)
            .addHeader("Accept", "application/json");

        if (StringUtils.isNotBlank(this.apiKey)) {
            builder.addHeader("x-api-key", this.apiKey);
        } else if (StringUtils.isNotBlank(this.sessionToken)) {
            builder.addHeader("X-Metabase-Session", this.sessionToken);
        }

        return builder;
    }

    public String get(String path) throws Exception {
        HttpRequest request = baseRequestBuilder(path, "GET").build();
        HttpResponse<String> response = this.httpClient.request(request, String.class);
        checkResponse(response);
        return response.getBody();
    }

    public <T> T get(String path, Class<T> clazz) throws Exception {
        String body = get(path);
        return MAPPER.readValue(body, clazz);
    }

    public <T> T get(String path, TypeReference<T> typeRef) throws Exception {
        String body = get(path);
        return MAPPER.readValue(body, typeRef);
    }

    public String post(String path, Object body) throws Exception {
        String jsonPayload = body != null ? (body instanceof String s ? s : MAPPER.writeValueAsString(body)) : "{}";
        HttpRequest request = baseRequestBuilder(path, "POST")
            .addHeader("Content-Type", "application/json")
            .body(HttpRequest.StringRequestBody.builder()
                .content(jsonPayload)
                .contentType("application/json")
                .build())
            .build();

        HttpResponse<String> response = this.httpClient.request(request, String.class);
        checkResponse(response);
        return response.getBody();
    }

    public <T> T post(String path, Object body, Class<T> clazz) throws Exception {
        String responseBody = post(path, body);
        return MAPPER.readValue(responseBody, clazz);
    }

    public <T> T post(String path, Object body, TypeReference<T> typeRef) throws Exception {
        String responseBody = post(path, body);
        return MAPPER.readValue(responseBody, typeRef);
    }

    private void checkResponse(HttpResponse<String> response) {
        if (response.getStatus().getCode() >= 400) {
            throw new MetabaseApiException(
                response.getStatus().getCode(),
                "Metabase request failed with status " + response.getStatus().getCode() + ": " + response.getBody()
            );
        }
    }

    @Override
    public void close() throws IOException {
        if (this.httpClient != null) {
            this.httpClient.close();
        }
    }
}
