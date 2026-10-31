package io.kestra.plugin.metabase;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.metabase.cards.Run;
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
class CardsTest {

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
    void testCardRunFetch() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        Run task = Run.builder()
            .url(Property.of(server.getUrl()))
            .apiKey(Property.of("test-api-key"))
            .cardId(Property.of(10))
            .fetchType(Property.of(FetchType.FETCH))
            .build();

        QueryOutput output = task.run(runContext);

        assertThat(output.getSize(), is(2L));
        assertThat(output.getRows(), hasSize(2));
        assertThat(output.getRows().get(0).get("id"), is(101));
        assertThat(output.getRows().get(0).get("title"), is("First Item"));
        assertThat(output.getRows().get(1).get("id"), is(102));
        assertThat(output.getRows().get(1).get("title"), is("Second Item"));
        assertThat(output.getRow(), nullValue());
        assertThat(output.getUri(), nullValue());
    }

    @Test
    void testCardRunFetchOne() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        Run task = Run.builder()
            .url(Property.of(server.getUrl()))
            .apiKey(Property.of("test-api-key"))
            .cardId(Property.of(10))
            .fetchType(Property.of(FetchType.FETCH_ONE))
            .build();

        QueryOutput output = task.run(runContext);

        assertThat(output.getSize(), is(1L));
        assertThat(output.getRow(), notNullValue());
        assertThat(output.getRow().get("id"), is(101));
        assertThat(output.getRow().get("title"), is("First Item"));
        assertThat(output.getRows(), nullValue());
        assertThat(output.getUri(), nullValue());
    }

    @Test
    void testCardRunStore() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        Run task = Run.builder()
            .url(Property.of(server.getUrl()))
            .apiKey(Property.of("test-api-key"))
            .cardId(Property.of(10))
            .fetchType(Property.of(FetchType.STORE))
            .build();

        QueryOutput output = task.run(runContext);

        assertThat(output.getSize(), is(2L));
        assertThat(output.getUri(), notNullValue());
        assertThat(output.getRows(), nullValue());
        assertThat(output.getRow(), nullValue());

        // Verify storage file contents with FileSerde
        List<Object> deserializedRows = new ArrayList<>();
        try (InputStream is = new BufferedInputStream(runContext.storage().getFile(output.getUri()), FileSerde.BUFFER_SIZE)) {
            FileSerde.read(is, deserializedRows::add);
        }

        assertThat(deserializedRows, hasSize(2));
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) deserializedRows.get(0);
        assertThat(first.get("id"), is(101));
        assertThat(first.get("title"), is("First Item"));
    }

    @Test
    void testCardRunNone() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        Run task = Run.builder()
            .url(Property.of(server.getUrl()))
            .apiKey(Property.of("test-api-key"))
            .cardId(Property.of(10))
            .fetchType(Property.of(FetchType.NONE))
            .build();

        QueryOutput output = task.run(runContext);

        assertThat(output.getSize(), is(2L));
        assertThat(output.getRow(), nullValue());
        assertThat(output.getRows(), nullValue());
        assertThat(output.getUri(), nullValue());
    }
}
