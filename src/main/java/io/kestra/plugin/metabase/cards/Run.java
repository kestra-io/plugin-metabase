package io.kestra.plugin.metabase.cards;

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
    title = "Run a saved Metabase question",
    description = "Executes a saved Metabase question (`POST /api/card/{id}/query`) and returns or stores the resulting rows."
)
@Plugin(
    examples = {
        @Example(
            title = "Run a saved question and store results in Kestra storage",
            full = true,
            code = """
                id: metabase_card_run
                namespace: company.team

                tasks:
                  - id: run
                    type: io.kestra.plugin.metabase.cards.Run
                    url: "{{ secret('METABASE_URL') }}"
                    apiKey: "{{ secret('METABASE_API_KEY') }}"
                    cardId: 42
                    fetchType: STORE
                """
        ),
        @Example(
            title = "Fetch rows of a saved question inline",
            full = true,
            code = """
                id: metabase_card_fetch
                namespace: company.team

                tasks:
                  - id: run
                    type: io.kestra.plugin.metabase.cards.Run
                    url: "{{ secret('METABASE_URL') }}"
                    apiKey: "{{ secret('METABASE_API_KEY') }}"
                    cardId: 10
                    fetchType: FETCH
                """
        )
    }
)
public class Run extends AbstractMetabaseTask implements RunnableTask<QueryOutput> {

    @Schema(
        title = "Card (Question) ID",
        description = "The ID of the saved question to run."
    )
    @PluginProperty(group = "main")
    @NotNull
    private Property<Integer> cardId;

    @Schema(
        title = "Query Parameters",
        description = "Optional parameter filter values passed to the question."
    )
    @PluginProperty(group = "main")
    private Property<List<Map<String, Object>>> parameters;

    @Schema(
        title = "Fetch Type",
        description = "The way results should be handled: `FETCH_ONE` (first row), `FETCH` (all rows inline), `STORE` (stream into internal storage as an Ion file), or `NONE` (no data returned)."
    )
    @PluginProperty(group = "processing")
    @Builder.Default
    private Property<FetchType> fetchType = Property.of(FetchType.FETCH);

    @Override
    public QueryOutput run(RunContext runContext) throws Exception {
        Integer renderedCardId = runContext.render(this.cardId).as(Integer.class).orElseThrow();
        FetchType renderedFetchType = runContext.render(this.fetchType).as(FetchType.class).orElse(FetchType.FETCH);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> renderedParameters = (List<Map<String, Object>>) (List<?>) runContext.render(this.parameters).asList(Map.class);

        runContext.logger().info("Executing saved question ID {} with fetchType {}", renderedCardId, renderedFetchType);

        Map<String, Object> requestBody = new HashMap<>();
        if (renderedParameters != null && !renderedParameters.isEmpty()) {
            requestBody.put("parameters", renderedParameters);
        }

        Map<String, Object> responseMap;
        try (MetabaseClient client = createClient(runContext)) {
            responseMap = client.post(
                "/api/card/" + renderedCardId + "/query",
                requestBody,
                new TypeReference<Map<String, Object>>() {}
            );
        }

        List<Map<String, Object>> records = QueryOutputHelper.parseRows(responseMap);
        runContext.logger().debug("Extracted {} rows from saved question ID {}", records.size(), renderedCardId);

        return QueryOutputHelper.processOutput(runContext, records, renderedFetchType);
    }
}
