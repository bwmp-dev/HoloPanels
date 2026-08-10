package dev.aether.holopanels.render;

import dev.aether.holopanels.api.ClickType;
import dev.aether.holopanels.api.PanelEntry;
import dev.aether.holopanels.model.ActionDefinition;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record ClickRegion(
        int firstLine,
        int lastLine,
        Optional<PanelEntry> entry,
        Map<ClickType, List<ActionDefinition>> actions
) {
    public boolean contains(int line) {
        return line >= firstLine && line <= lastLine;
    }
}
