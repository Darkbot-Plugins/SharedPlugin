package dev.shared.canda.remote.transport;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import dev.shared.canda.remote.config.RemotePanelConfig;

public class HttpCommandReceiver implements CommandReceiver {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    @Override
    public Optional<String> poll(RemotePanelConfig config, String botPublicId) {
        if (config == null) {
            return Optional.empty();
        }

        if (config.commandUrl == null || config.commandUrl.trim().isEmpty()) {
            return Optional.empty();
        }

        try {
            String url = withBotPublicId(config.commandUrl.trim(), botPublicId);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(2))
                    .header("Accept", "application/json")
                    .GET();
            if (config.apiToken != null && !config.apiToken.trim().isEmpty()) {
                builder.header("Authorization", "Bearer " + config.apiToken.trim());
            }

            HttpResponse<String> response = this.http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String body = response.body() == null ? "" : response.body().trim();
                if (!body.isEmpty()) {
                    return Optional.of(body);
                }
            }
        } catch (Exception ignored) {
            // Network errors are expected when no backend is connected yet.
        }
        return Optional.empty();
    }

    private String withBotPublicId(String commandUrl, String botPublicId) {
        if (botPublicId == null || botPublicId.trim().isEmpty()) {
            return commandUrl;
        }

        if (commandUrl.contains("botPublicId=")) {
            return commandUrl;
        }

        String encoded = URLEncoder.encode(botPublicId.trim(), StandardCharsets.UTF_8);
        return commandUrl + (commandUrl.contains("?") ? "&" : "?") + "botPublicId=" + encoded;
    }
}
