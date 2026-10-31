package io.kestra.plugin.metabase.dashboards;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.metabase.AbstractMetabaseTask;
import io.kestra.plugin.metabase.MetabaseClient;
import io.kestra.plugin.metabase.models.DashboardResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
    title = "Get a Metabase dashboard",
    description = "Retrieves details and cards of a specific dashboard (`GET /api/dashboard/{id}`) from Metabase."
)
@Plugin(
    examples = {
        @Example(
            title = "Get details and embedded cards for a dashboard",
            full = true,
            code = """
                id: metabase_get_dashboard
                namespace: company.team

                tasks:
                  - id: get
                    type: io.kestra.plugin.metabase.dashboards.Get
                    url: "{{ secret('METABASE_URL') }}"
                    apiKey: "{{ secret('METABASE_API_KEY') }}"
                    dashboardId: 5
                """
        )
    }
)
public class Get extends AbstractMetabaseTask implements RunnableTask<Get.Output> {

    @Schema(
        title = "Dashboard ID",
        description = "The ID of the Metabase dashboard to retrieve."
    )
    @PluginProperty(group = "main")
    @NotNull
    private Property<Integer> dashboardId;

    @Override
    @SuppressWarnings("unchecked")
    public Output run(RunContext runContext) throws Exception {
        Integer renderedDashboardId = runContext.render(this.dashboardId).as(Integer.class).orElseThrow();
        runContext.logger().info("Fetching Metabase dashboard ID {}", renderedDashboardId);

        DashboardResponse dashboardResponse;
        try (MetabaseClient client = createClient(runContext)) {
            dashboardResponse = client.get("/api/dashboard/" + renderedDashboardId, DashboardResponse.class);
        }

        Map<String, Object> dashboard = dashboardResponse != null ? dashboardResponse.toMap() : Collections.emptyMap();
        java.util.List<Map<String, Object>> cards = Collections.emptyList();
        if (dashboardResponse != null) {
            if (dashboardResponse.getDashcards() != null) {
                cards = dashboardResponse.getDashcards();
            } else if (dashboardResponse.getOrderedCards() != null) {
                cards = dashboardResponse.getOrderedCards();
            }
        }

        return Output.builder()
            .dashboard(dashboard)
            .cards(cards)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Dashboard details",
            description = "Complete metadata of the Metabase dashboard."
        )
        private final Map<String, Object> dashboard;

        @Schema(
            title = "Dashboard cards",
            description = "List of cards/dashcards included in the dashboard."
        )
        private final java.util.List<Map<String, Object>> cards;
    }
}
