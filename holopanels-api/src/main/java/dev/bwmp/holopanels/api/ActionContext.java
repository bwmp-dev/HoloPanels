package dev.bwmp.holopanels.api;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record ActionContext(
        Player player,
        NamespacedKey boardId,
        NamespacedKey viewId,
        String panelId,
        ClickType clickType,
        Optional<PanelEntry> selectedEntry,
        Map<String, String> arguments,
        Map<String, String> sessionState
) {
    public ActionContext {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(boardId, "boardId");
        Objects.requireNonNull(viewId, "viewId");
        Objects.requireNonNull(panelId, "panelId");
        Objects.requireNonNull(clickType, "clickType");
        selectedEntry = Objects.requireNonNull(selectedEntry, "selectedEntry");
        arguments = Map.copyOf(arguments);
        sessionState = Map.copyOf(sessionState);
    }
}
