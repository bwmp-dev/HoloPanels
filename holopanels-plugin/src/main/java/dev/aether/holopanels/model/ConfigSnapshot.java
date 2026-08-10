package dev.aether.holopanels.model;

import org.bukkit.NamespacedKey;

import java.util.Map;

public record ConfigSnapshot(
        PluginSettings settings,
        Map<NamespacedKey, BoardDefinition> boards,
        Map<NamespacedKey, ViewDefinition> views
) {
    public ConfigSnapshot {
        boards = Map.copyOf(boards);
        views = Map.copyOf(views);
    }
}
