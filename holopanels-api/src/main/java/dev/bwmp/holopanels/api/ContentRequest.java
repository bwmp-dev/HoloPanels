package dev.bwmp.holopanels.api;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record ContentRequest(
        Player player,
        NamespacedKey boardId,
        NamespacedKey viewId,
        String panelId,
        Optional<PanelEntry> selectedEntry,
        Map<String, String> sessionState
) {
    public ContentRequest {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(boardId, "boardId");
        Objects.requireNonNull(viewId, "viewId");
        Objects.requireNonNull(panelId, "panelId");
        selectedEntry = Objects.requireNonNull(selectedEntry, "selectedEntry");
        sessionState = Map.copyOf(sessionState);
    }
}
