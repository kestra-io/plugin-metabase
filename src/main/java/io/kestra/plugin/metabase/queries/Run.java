package io.kestra.plugin.metabase.queries;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.metabase.AbstractMetabaseTask;
import io.kestra.plugin.metabase.MetabaseClient;
import io.kestra.plugin.metabase.QueryOutput;
import io.kestra.plugin.metabase.QueryOutputHelper;
import io.kestra.plugin.metabase.models.QueryResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Run a native Metabase query",
    description = "Executes a native SQL dataset query (`POST /api/dataset`) directly against a connected database in Metabase."
)
@Plugin(
    examples = {
        @Example(
            title = "Execute a native query and stream rows to Kestra storage",
            full = true,
            code = """
                id: metabase_native_query
                namespace: company.team

                tasks:
                  - id: run
                    type: io.kestra.plugin.metabase.queries.Run
                    url: "{{ secret('METABASE_URL') }}"
                    apiKey: "{{ secret('METABASE_API_KEY') }}"
                    databaseId: 2
                    nativeQuery: "SELECT id, name, created_at FROM orders WHERE status = 'COMPLETED'"
                    fetchType: STORE
                """
        ),
        @Example(
            title = "Fetch single row from a native query",
            full = true,
            code = """
                id: metabase_native_fetch_one
                namespace: company.team

                tasks:
                  - id: get_count
                    type: io.kestra.plugin.metabase.queries.Run
                    url: "{{ secret('METABASE_URL') }}"
                    apiKey: "{{ secret('METABASE_API_KEY') }}"
                    databaseId: 2
                    nativeQuery: "SELECT COUNT(*) as total FROM users"
                    fetchType: FETCH_ONE
                """
        )
    }
)
public class Run extends AbstractMetabaseTask implements RunnableTask<QueryOutput> {

    @Schema(
        title = "Database ID",
        description = "The ID of the Metabase database to execute the query on."
    )
    @PluginProperty(group = "main")
    @NotNull
    private Property<Integer> databaseId;

    @Schema(
        title = "Native SQL Query",
        description = "The native SQL query string to run."
    )
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> nativeQuery;

    @Schema(
        title = "Template Tags",
        description = "Optional Metabase template tags / variable bindings for the native query."
    )
    @PluginProperty(group = "main")
    private Property<Map<String, Object>> templateTags;

    @Schema(
        title = "Fetch Type",
        description = "The way results should be handled: `FETCH_ONE` (first row), `FETCH` (all rows inline), `STORE` (stream into internal storage as an Ion file), or `NONE` (no data returned)."
    )
    @PluginProperty(group = "processing")
    @Builder.Default
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public QueryOutput run(RunContext runContext) throws Exception {
        Integer renderedDatabaseId = runContext.render(this.databaseId).as(Integer.class).orElseThrow();
        String renderedQuery = runContext.render(this.nativeQuery).as(String.class).orElseThrow();
        FetchType renderedFetchType = runContext.render(this.fetchType).as(FetchType.class).orElse(FetchType.FETCH);
        Map<String, Object> renderedTemplateTags = runContext.render(this.templateTags).asMap(String.class, Object.class);

        runContext.logger().info("Executing native query on database ID {} with fetchType {}", renderedDatabaseId, renderedFetchType);

        Map<String, Object> nativePayload = new HashMap<>();
        nativePayload.put("query", renderedQuery);
        if (renderedTemplateTags != null && !renderedTemplateTags.isEmpty()) {
            nativePayload.put("template-tags", renderedTemplateTags);
        } else {
            nativePayload.put("template-tags", Map.of());
        }

        Map<String, Object> requestBody = Map.of(
            "database", renderedDatabaseId,
            "type", "native",
            "native", nativePayload
        );

        QueryResponse response;
        try (MetabaseClient client = createClient(runContext)) {
            response = client.post(
                "/api/dataset",
                requestBody,
                QueryResponse.class
            );
        }

        List<Map<String, Object>> records = QueryOutputHelper.parseRows(response);
        runContext.logger().debug("Extracted {} rows from dataset query on database ID {}", records.size(), renderedDatabaseId);

        return QueryOutputHelper.processOutput(runContext, records, renderedFetchType);
    }
}
