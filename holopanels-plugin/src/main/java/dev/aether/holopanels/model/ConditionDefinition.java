package dev.aether.holopanels.model;

import java.util.List;
import java.util.Map;

public record ConditionDefinition(Kind kind, Map<String, String> arguments, List<ConditionDefinition> children) {
    public enum Kind {
        ALWAYS,
        ALL,
        ANY,
        NOT,
        PERMISSION,
        SELECTED,
        ENTRY_ATTRIBUTE,
        SESSION_STATE,
        CUSTOM
    }

    public ConditionDefinition {
        arguments = Map.copyOf(arguments);
        children = List.copyOf(children);
    }

    public static ConditionDefinition always() {
        return new ConditionDefinition(Kind.ALWAYS, Map.of(), List.of());
    }
}
