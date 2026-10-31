package io.kestra.plugin.metabase;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class MetabaseClientTest {

    @Inject
    private RunContextFactory runContextFactory;

    private MetabaseMockServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MetabaseMockServer();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void testApiKeyAuth() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        try (MetabaseClient client = new MetabaseClient(
            runContext,
            server.getUrl(),
            "secret-api-key-123",
            null,
            null,
            null,
            Duration.ofSeconds(10)
        )) {
            String response = client.get("/api/dashboard");
            assertThat(response, notNullValue());

            List<MetabaseMockServer.RecordedRequest> requests = server.getRecordedRequests();
            assertThat(requests, hasSize(1));
            assertThat(requests.get(0).headers().get("x-api-key"), contains("secret-api-key-123"));
        }
    }

    @Test
    void testSessionTokenAuth() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        try (MetabaseClient client = new MetabaseClient(
            runContext,
            server.getUrl(),
            null,
            "session-token-abc",
            null,
            null,
            Duration.ofSeconds(10)
        )) {
            String response = client.get("/api/dashboard");
            assertThat(response, notNullValue());

            List<MetabaseMockServer.RecordedRequest> requests = server.getRecordedRequests();
            assertThat(requests, hasSize(1));
            assertThat(requests.get(0).headers().get("X-Metabase-Session"), contains("session-token-abc"));
        }
    }

    @Test
    void testUsernamePasswordAuthFallback() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        try (MetabaseClient client = new MetabaseClient(
            runContext,
            server.getUrl(),
            null,
            null,
            "admin@example.com",
            "password123",
            Duration.ofSeconds(10)
        )) {
            String response = client.get("/api/dashboard");
            assertThat(response, notNullValue());

            List<MetabaseMockServer.RecordedRequest> requests = server.getRecordedRequests();
            // First call was POST /api/session, second call was GET /api/dashboard
            assertThat(requests, hasSize(2));
            assertThat(requests.get(0).path(), is("/api/session"));
            assertThat(requests.get(1).path(), is("/api/dashboard"));
            assertThat(requests.get(1).headers().get("X-Metabase-Session"), contains("test-session-token-xyz"));
        }
    }
}
