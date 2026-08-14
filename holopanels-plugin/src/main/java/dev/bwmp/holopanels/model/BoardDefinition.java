package dev.bwmp.holopanels.model;

import org.bukkit.NamespacedKey;

import java.util.Optional;

public record BoardDefinition(
        NamespacedKey id,
        NamespacedKey rootView,
        Optional<BoardLocation> location,
        double visibilityDistance,
        double clickDistance,
        ConditionDefinition condition
) {
}
