package dev.shared.canda.remote.transport;

import dev.shared.canda.remote.config.RemotePanelConfig;
import dev.shared.canda.remote.telemetry.RemoteSnapshot;

public interface TelemetrySender {
    boolean send(RemotePanelConfig config, RemoteSnapshot snapshot);
}
