package io.kestra.plugin.metabase.databases;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.metabase.AbstractMetabaseTask;
import io.kestra.plugin.metabase.MetabaseClient;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger Metabase database field values rescan",
    description = "Triggers a field values rescan (`POST /api/database/{id}/rescan_values`) for a specified database in Metabase."
)
@Plugin(
    examples = {
        @Example(
            title = "Rescan database field values after refreshing table data",
            full = true,
            code = """
                id: metabase_rescan_values
                namespace: company.team

                tasks:
                  - id: rescan
                    type: io.kestra.plugin.metabase.databases.RescanValues
                    url: "{{ secret('METABASE_URL') }}"
                    apiKey: "{{ secret('METABASE_API_KEY') }}"
                    databaseId: 3
                """
        )
    }
)
public class RescanValues extends AbstractMetabaseTask implements RunnableTask<RescanValues.Output> {

    @Schema(
        title = "Database ID",
        description = "The ID of the Metabase database to rescan field values for."
    )
    @PluginProperty(group = "main")
    @NotNull
    private Property<Integer> databaseId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Integer renderedDatabaseId = runContext.render(this.databaseId).as(Integer.class).orElseThrow();
        runContext.logger().info("Triggering field values rescan for Metabase database ID {}", renderedDatabaseId);

        try (MetabaseClient client = createClient(runContext)) {
            client.post("/api/database/" + renderedDatabaseId + "/rescan_values", null);
        }

        return Output.builder()
            .databaseId(renderedDatabaseId)
            .success(true)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Database ID",
            description = "The ID of the database that was rescanned."
        )
        private final Integer databaseId;

        @Schema(
            title = "Operation success",
            description = "Whether the field values rescan was successfully triggered."
        )
        private final Boolean success;
    }
}
