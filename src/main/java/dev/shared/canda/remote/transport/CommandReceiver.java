package dev.shared.canda.remote.transport;

import java.util.Optional;

import dev.shared.canda.remote.config.RemotePanelConfig;

public interface CommandReceiver {
    Optional<String> poll(RemotePanelConfig config, String botPublicId);
}
