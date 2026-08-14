package dev.bwmp.holopanels.api;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;

public record EntryRequest(
        Player player,
        NamespacedKey boardId,
        NamespacedKey viewId,
        String panelId,
        Map<String, String> sessionState
) {
    public EntryRequest {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(boardId, "boardId");
        Objects.requireNonNull(viewId, "viewId");
        Objects.requireNonNull(panelId, "panelId");
        sessionState = Map.copyOf(sessionState);
    }
}
