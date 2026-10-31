package io.kestra.plugin.metabase;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Duration;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractMetabaseTask extends Task {

    @Schema(
        title = "Metabase server URL",
        description = "Base URL of the Metabase instance (e.g. https://metabase.example.com or http://localhost:3000)"
    )
    @PluginProperty(group = "connection")
    @NotNull
    protected Property<String> url;

    @Schema(
        title = "Metabase API Key",
        description = "API key for Metabase authentication (recommended for Metabase v0.47+). Sent in the `x-api-key` header."
    )
    @PluginProperty(group = "connection", secret = true)
    @ToString.Exclude
    protected Property<String> apiKey;

    @Schema(
        title = "Metabase Session Token",
        description = "Session token for Metabase authentication. Sent in the `X-Metabase-Session` header."
    )
    @PluginProperty(group = "connection", secret = true)
    @ToString.Exclude
    protected Property<String> sessionToken;

    @Schema(
        title = "Metabase Username",
        description = "Username/email to obtain a session token via `POST /api/session` if API key is not provided."
    )
    @PluginProperty(group = "connection")
    protected Property<String> username;

    @Schema(
        title = "Metabase Password",
        description = "Password to obtain a session token via `POST /api/session`."
    )
    @PluginProperty(group = "connection", secret = true)
    @ToString.Exclude
    protected Property<String> password;

    @Schema(
        title = "HTTP Request Timeout",
        description = "Timeout duration for HTTP requests made to Metabase."
    )
    @PluginProperty(group = "connection")
    @Builder.Default
    protected Property<Duration> timeout = Property.of(Duration.ofSeconds(60));

    /**
     * Builds and returns a configured, authenticated MetabaseClient.
     */
    protected MetabaseClient createClient(RunContext runContext) throws Exception {
        String renderedUrl = runContext.render(this.url).as(String.class).orElseThrow();
        String renderedApiKey = runContext.render(this.apiKey).as(String.class).orElse(null);
        String renderedSessionToken = runContext.render(this.sessionToken).as(String.class).orElse(null);
        String renderedUsername = runContext.render(this.username).as(String.class).orElse(null);
        String renderedPassword = runContext.render(this.password).as(String.class).orElse(null);
        Duration renderedTimeout = runContext.render(this.timeout).as(Duration.class).orElse(Duration.ofSeconds(60));

        return new MetabaseClient(
            runContext,
            renderedUrl,
            renderedApiKey,
            renderedSessionToken,
            renderedUsername,
            renderedPassword,
            renderedTimeout
        );
    }
}
