package dev.aether.holopanels.model;

import org.bukkit.NamespacedKey;

import java.util.LinkedHashMap;
import java.util.Map;

public record ViewDefinition(NamespacedKey id, Map<String, PanelDefinition> panels) {
    public ViewDefinition {
        panels = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(panels));
    }
}
