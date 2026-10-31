package io.kestra.plugin.metabase;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.metabase.dashboards.Get;
import io.kestra.plugin.metabase.dashboards.List;
import io.kestra.plugin.metabase.dashboards.Trigger;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class DashboardsTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private FlowRepositoryInterface flowRepository;

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
    void testDashboardList() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        List task = List.builder()
            .url(Property.of(server.getUrl()))
            .apiKey(Property.of("test-api-key"))
            .build();

        List.Output output = task.run(runContext);

        assertThat(output.getSize(), is(2));
        assertThat(output.getDashboards(), hasSize(2));
        assertThat(output.getDashboards().get(0).get("name"), is("Executive Overview"));
        assertThat(output.getDashboards().get(1).get("name"), is("Operations"));
    }

    @Test
    void testDashboardGet() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        Get task = Get.builder()
            .url(Property.of(server.getUrl()))
            .apiKey(Property.of("test-api-key"))
            .dashboardId(Property.of(1))
            .build();

        Get.Output output = task.run(runContext);

        assertThat(output.getDashboard(), notNullValue());
        assertThat(output.getDashboard().get("id"), is(1));
        assertThat(output.getDashboard().get("name"), is("Executive Overview"));
        assertThat(output.getCards(), hasSize(2));
        assertThat(output.getCards().get(0).get("id"), is(11));
    }

    @Test
    void testDashboardTriggerWatermarkFlow() throws Exception {
        String tenantId = IdUtils.create();
        String namespace = "io.kestra." + IdUtils.create();
        String flowId = "flow-" + IdUtils.create();
        String triggerId = "trigger-" + IdUtils.create();

        Flow flow = Flow.builder()
            .tenantId(tenantId)
            .id(flowId)
            .namespace(namespace)
            .revision(1)
            .tasks(Collections.emptyList())
            .build();
        flowRepository.create(GenericFlow.of(flow));

        Trigger trigger = Trigger.builder()
            .id(triggerId)
            .type(Trigger.class.getName())
            .url(Property.of(server.getUrl()))
            .apiKey(Property.of("test-api-key"))
            .onStartup(Property.of(false))
            .build();

        TriggerContext triggerContext = TriggerContext.builder()
            .tenantId(tenantId)
            .triggerId(triggerId)
            .namespace(namespace)
            .flowId(flowId)
            .build();

        RunContext runContext = runContextFactory.of(flow, trigger);
        runContext = runContextFactory.initializer().forScheduler((io.kestra.core.runners.DefaultRunContext) runContext, triggerContext, trigger);

        ConditionContext conditionContext = ConditionContext.builder()
            .runContext(runContext)
            .flow(flow)
            .build();

        // 1. First evaluation: watermark is empty, onStartup=false -> initializes watermark to max (2026-10-20), returns empty
        Optional<Execution> eval1 = trigger.evaluate(conditionContext, triggerContext);
        assertThat(eval1.isEmpty(), is(true));

        // 2. Add a new dashboard updated on 2026-10-31
        server.addDashboard(3, "Real-time Sales", "2026-10-31T08:00:00Z");

        // 3. Second evaluation: detects new dashboard, returns Execution
        Optional<Execution> eval2 = trigger.evaluate(conditionContext, triggerContext);
        assertThat(eval2.isPresent(), is(true));
        Execution execution = eval2.get();
        assertThat(execution.getTrigger(), notNullValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> triggerVariables = (Map<String, Object>) execution.getTrigger().getVariables();
        assertThat(triggerVariables.get("size"), is(1));

        // 4. Third evaluation: no newer dashboard, returns empty
        Optional<Execution> eval3 = trigger.evaluate(conditionContext, triggerContext);
        assertThat(eval3.isEmpty(), is(true));
    }
}
