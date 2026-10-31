package io.kestra.plugin.metabase.dashboards;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.metabase.AbstractMetabaseTask;
import io.kestra.plugin.metabase.MetabaseClient;
import io.kestra.plugin.metabase.models.DashboardResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Collections;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List Metabase dashboards",
    description = "Retrieves the list of all accessible dashboards (`GET /api/dashboard`) from Metabase."
)
@Plugin(
    examples = {
        @Example(
            title = "List all dashboards in Metabase",
            full = true,
            code = """
                id: metabase_list_dashboards
                namespace: company.team

                tasks:
                  - id: list
                    type: io.kestra.plugin.metabase.dashboards.List
                    url: "{{ secret('METABASE_URL') }}"
                    apiKey: "{{ secret('METABASE_API_KEY') }}"
                """
        )
    }
)
public class List extends AbstractMetabaseTask implements RunnableTask<List.Output> {

    @Override
    public Output run(RunContext runContext) throws Exception {
        runContext.logger().info("Fetching list of Metabase dashboards");

        java.util.List<DashboardResponse> dashboardResponses;
        try (MetabaseClient client = createClient(runContext)) {
            dashboardResponses = client.get("/api/dashboard", new TypeReference<java.util.List<DashboardResponse>>() {});
        }

        java.util.List<Map<String, Object>> dashboards = dashboardResponses != null
            ? dashboardResponses.stream().map(DashboardResponse::toMap).toList()
            : Collections.emptyList();

        int count = dashboards.size();
        runContext.logger().info("Retrieved {} Metabase dashboards", count);

        return Output.builder()
            .dashboards(dashboards)
            .size(count)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Dashboards",
            description = "List of Metabase dashboards."
        )
        private final java.util.List<Map<String, Object>> dashboards;

        @Schema(
            title = "Dashboards count",
            description = "Total number of dashboards returned."
        )
        private final Integer size;
    }
}
