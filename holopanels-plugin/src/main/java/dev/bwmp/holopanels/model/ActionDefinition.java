package dev.bwmp.holopanels.model;

import java.util.Map;

public record ActionDefinition(String type, Map<String, String> arguments, boolean continueOnFailure) {
    public ActionDefinition {
        arguments = Map.copyOf(arguments);
    }
}
