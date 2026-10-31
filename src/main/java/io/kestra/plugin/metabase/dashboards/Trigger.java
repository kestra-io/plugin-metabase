package io.kestra.plugin.metabase.dashboards;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.*;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValue;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.plugin.metabase.MetabaseClient;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Metabase dashboard polling trigger",
    description = "Triggers a flow when a Metabase dashboard is created or updated since the last evaluation, maintaining a watermark in the namespace KV store."
)
@Plugin(
    examples = {
        @Example(
            title = "Watch for dashboard changes in Metabase and trigger a flow",
            full = true,
            code = """
                id: on_metabase_change
                namespace: company.team

                triggers:
                  - id: watch
                    type: io.kestra.plugin.metabase.dashboards.Trigger
                    url: "{{ secret('METABASE_URL') }}"
                    apiKey: "{{ secret('METABASE_API_KEY') }}"
                    interval: PT5M

                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: "Metabase dashboard change detected. Changed count: {{ trigger.size }}"
                """
        )
    }
)
public class Trigger extends AbstractTrigger implements PollingTriggerInterface, TriggerOutput<Trigger.Output> {

    @Schema(
        title = "Metabase server URL",
        description = "Base URL of the Metabase instance (e.g. https://metabase.example.com or http://localhost:3000)"
    )
    @PluginProperty(group = "connection")
    @NotNull
    private Property<String> url;

    @Schema(
        title = "Metabase API Key",
        description = "API key for Metabase authentication (recommended for Metabase v0.47+). Sent in the `x-api-key` header."
    )
    @PluginProperty(group = "connection", secret = true)
    @ToString.Exclude
    private Property<String> apiKey;

    @Schema(
        title = "Metabase Session Token",
        description = "Session token for Metabase authentication. Sent in the `X-Metabase-Session` header."
    )
    @PluginProperty(group = "connection", secret = true)
    @ToString.Exclude
    private Property<String> sessionToken;

    @Schema(
        title = "Metabase Username",
        description = "Username/email to obtain a session token via `POST /api/session` if API key is not provided."
    )
    @PluginProperty(group = "connection")
    private Property<String> username;

    @Schema(
        title = "Metabase Password",
        description = "Password to obtain a session token via `POST /api/session`."
    )
    @PluginProperty(group = "connection", secret = true)
    @ToString.Exclude
    private Property<String> password;

    @Schema(
        title = "HTTP Request Timeout",
        description = "Timeout duration for HTTP requests made to Metabase."
    )
    @PluginProperty(group = "connection")
    @Builder.Default
    private Property<Duration> timeout = Property.ofValue(Duration.ofSeconds(60));

    @Schema(
        title = "Polling interval",
        description = "The interval between polling checks."
    )
    @PluginProperty(group = "execution")
    @Builder.Default
    private final Duration interval = Duration.ofMinutes(5);

    @Schema(
        title = "Trigger on startup",
        description = "If true, triggers for existing dashboards on initial run. If false (default), sets initial watermark without triggering."
    )
    @PluginProperty(group = "execution")
    @Builder.Default
    private Property<Boolean> onStartup = Property.ofValue(false);

    @Override
    public Duration getInterval() {
        return this.interval;
    }

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        RunContext runContext = conditionContext.getRunContext();
        String namespace = conditionContext.getFlow().getNamespace();

        String renderedUrl = runContext.render(this.url).as(String.class).orElseThrow();
        String renderedApiKey = runContext.render(this.apiKey).as(String.class).orElse(null);
        String renderedSessionToken = runContext.render(this.sessionToken).as(String.class).orElse(null);
        String renderedUsername = runContext.render(this.username).as(String.class).orElse(null);
        String renderedPassword = runContext.render(this.password).as(String.class).orElse(null);
        Duration renderedTimeout = runContext.render(this.timeout).as(Duration.class).orElse(Duration.ofSeconds(60));
        boolean renderedOnStartup = runContext.render(this.onStartup).as(Boolean.class).orElse(false);

        KVStore kvStore = runContext.namespaceKv(namespace);
        String watermarkKey = "metabase_dashboards_watermark_" + context.getTriggerId();

        Optional<KVValue> currentKv = kvStore.getValue(watermarkKey);
        String lastWatermark = currentKv.map(v -> v.value() != null ? v.value().toString() : null).orElse(null);

        java.util.List<Map<String, Object>> allDashboards;
        try (MetabaseClient client = new MetabaseClient(
            runContext,
            renderedUrl,
            renderedApiKey,
            renderedSessionToken,
            renderedUsername,
            renderedPassword,
            renderedTimeout
        )) {
            allDashboards = client.get("/api/dashboard", new TypeReference<java.util.List<Map<String, Object>>>() {});
        }

        if (allDashboards == null || allDashboards.isEmpty()) {
            return Optional.empty();
        }

        String maxTimestamp = findMaxTimestamp(allDashboards);

        if (lastWatermark == null) {
            // Initial poll
            if (maxTimestamp != null) {
                kvStore.put(watermarkKey, new KVValueAndMetadata(null, maxTimestamp));
            }

            if (!renderedOnStartup) {
                runContext.logger().info("Initialized Metabase dashboard watermark to {}", maxTimestamp);
                return Optional.empty();
            }

            Output output = Output.builder()
                .dashboards(allDashboards)
                .size(allDashboards.size())
                .build();
            return Optional.of(TriggerService.generateExecution(this, conditionContext, context, output));
        }

        // Subsequent poll: filter dashboards newer than lastWatermark
        java.util.List<Map<String, Object>> changedDashboards = new ArrayList<>();
        String newMaxTimestamp = lastWatermark;

        for (Map<String, Object> dashboard : allDashboards) {
            String ts = getDashboardTimestamp(dashboard);
            if (ts != null && ts.compareTo(lastWatermark) > 0) {
                changedDashboards.add(dashboard);
                if (newMaxTimestamp == null || ts.compareTo(newMaxTimestamp) > 0) {
                    newMaxTimestamp = ts;
                }
            }
        }

        if (changedDashboards.isEmpty()) {
            return Optional.empty();
        }

        // Advance watermark in KV Store
        kvStore.put(watermarkKey, new KVValueAndMetadata(null, newMaxTimestamp));
        runContext.logger().info("Found {} updated/created dashboards. Advanced watermark to {}", changedDashboards.size(), newMaxTimestamp);

        Output output = Output.builder()
            .dashboards(changedDashboards)
            .size(changedDashboards.size())
            .build();

        return Optional.of(TriggerService.generateExecution(this, conditionContext, context, output));
    }

    private String getDashboardTimestamp(Map<String, Object> dashboard) {
        if (dashboard == null) return null;
        Object updatedAt = dashboard.get("updated_at");
        if (updatedAt != null) return updatedAt.toString();
        Object createdAt = dashboard.get("created_at");
        if (createdAt != null) return createdAt.toString();
        return null;
    }

    private String findMaxTimestamp(java.util.List<Map<String, Object>> dashboards) {
        String max = null;
        for (Map<String, Object> dashboard : dashboards) {
            String ts = getDashboardTimestamp(dashboard);
            if (ts != null && (max == null || ts.compareTo(max) > 0)) {
                max = ts;
            }
        }
        return max;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Dashboards",
            description = "List of new or updated Metabase dashboards."
        )
        private final java.util.List<Map<String, Object>> dashboards;

        @Schema(
            title = "Dashboards count",
            description = "Number of new or updated dashboards."
        )
        private final Integer size;
    }
}
