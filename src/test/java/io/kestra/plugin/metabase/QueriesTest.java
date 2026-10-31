package io.kestra.plugin.metabase;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.metabase.queries.Run;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class QueriesTest {

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
    void testQueryRunFetch() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        Run task = Run.builder()
            .url(Property.of(server.getUrl()))
            .apiKey(Property.of("test-api-key"))
            .databaseId(Property.of(1))
            .nativeQuery(Property.of("SELECT COUNT(*) AS total FROM users"))
            .fetchType(Property.of(FetchType.FETCH))
            .build();

        QueryOutput output = task.run(runContext);

        assertThat(output.getSize(), is(1L));
        assertThat(output.getRows(), hasSize(1));
        assertThat(output.getRows().get(0).get("total"), is(42));
    }

    @Test
    void testQueryRunFetchOne() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        Run task = Run.builder()
            .url(Property.of(server.getUrl()))
            .apiKey(Property.of("test-api-key"))
            .databaseId(Property.of(1))
            .nativeQuery(Property.of("SELECT COUNT(*) AS total FROM users"))
            .fetchType(Property.of(FetchType.FETCH_ONE))
            .build();

        QueryOutput output = task.run(runContext);

        assertThat(output.getSize(), is(1L));
        assertThat(output.getRow(), notNullValue());
        assertThat(output.getRow().get("total"), is(42));
    }

    @Test
    void testQueryRunStore() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        Run task = Run.builder()
            .url(Property.of(server.getUrl()))
            .apiKey(Property.of("test-api-key"))
            .databaseId(Property.of(1))
            .nativeQuery(Property.of("SELECT COUNT(*) AS total FROM users"))
            .fetchType(Property.of(FetchType.STORE))
            .build();

        QueryOutput output = task.run(runContext);

        assertThat(output.getSize(), is(1L));
        assertThat(output.getUri(), notNullValue());

        List<Object> deserializedRows = new ArrayList<>();
        try (InputStream is = new BufferedInputStream(runContext.storage().getFile(output.getUri()), FileSerde.BUFFER_SIZE)) {
            FileSerde.read(is, deserializedRows::add);
        }

        assertThat(deserializedRows, hasSize(1));
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) deserializedRows.get(0);
        assertThat(first.get("total"), is(42));
    }
}
