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
    title = "Trigger Metabase database schema sync",
    description = "Triggers a schema synchronization (`POST /api/database/{id}/sync_schema`) for a specified database in Metabase."
)
@Plugin(
    examples = {
        @Example(
            title = "Sync database schema after a data warehouse load",
            full = true,
            code = """
                id: metabase_sync_schema
                namespace: company.team

                tasks:
                  - id: sync
                    type: io.kestra.plugin.metabase.databases.SyncSchema
                    url: "{{ secret('METABASE_URL') }}"
                    apiKey: "{{ secret('METABASE_API_KEY') }}"
                    databaseId: 3
                """
        )
    }
)
public class SyncSchema extends AbstractMetabaseTask implements RunnableTask<SyncSchema.Output> {

    @Schema(
        title = "Database ID",
        description = "The ID of the Metabase database to sync schema for."
    )
    @PluginProperty(group = "main")
    @NotNull
    private Property<Integer> databaseId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Integer renderedDatabaseId = runContext.render(this.databaseId).as(Integer.class).orElseThrow();
        runContext.logger().info("Triggering schema sync for Metabase database ID {}", renderedDatabaseId);

        try (MetabaseClient client = createClient(runContext)) {
            client.post("/api/database/" + renderedDatabaseId + "/sync_schema", null);
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
            description = "The ID of the database that was synced."
        )
        private final Integer databaseId;

        @Schema(
            title = "Operation success",
            description = "Whether the schema sync was successfully triggered."
        )
        private final Boolean success;
    }
}
