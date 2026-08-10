package dev.aether.holopanels.model;

import dev.aether.holopanels.api.ClickType;

import java.util.List;
import java.util.Map;

public record ButtonDefinition(
        String id,
        String text,
        ConditionDefinition condition,
        Map<ClickType, List<ActionDefinition>> clicks
) {
    public ButtonDefinition {
        clicks = Map.copyOf(clicks);
    }
}
