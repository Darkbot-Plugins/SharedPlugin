package dev.shared.canda.remote.transport;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import dev.shared.canda.remote.config.RemotePanelConfig;
import dev.shared.canda.remote.telemetry.RemoteSnapshot;

public class HttpTelemetrySender implements TelemetrySender {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    @Override
    public boolean send(RemotePanelConfig config, RemoteSnapshot snapshot) {
        if (config == null || snapshot == null) {
            return false;
        }

        if (config.telemetryUrl == null || config.telemetryUrl.trim().isEmpty()) {
            // Local skeleton mode: no backend URL configured yet.
            return true;
        }

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(config.telemetryUrl.trim()))
                    .timeout(Duration.ofSeconds(2))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(snapshot.toJson()));
            if (config.apiToken != null && !config.apiToken.trim().isEmpty()) {
                builder.header("Authorization", "Bearer " + config.apiToken.trim());
            }

            HttpResponse<String> response = this.http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }
}
