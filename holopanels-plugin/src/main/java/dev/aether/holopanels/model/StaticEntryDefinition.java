package dev.aether.holopanels.model;

import java.util.Map;

public record StaticEntryDefinition(
        String id,
        String label,
        Map<String, String> fields,
        Map<String, String> attributes
) {
    public StaticEntryDefinition {
        fields = Map.copyOf(fields);
        attributes = Map.copyOf(attributes);
    }
}
