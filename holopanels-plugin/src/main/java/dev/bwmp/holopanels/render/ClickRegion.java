package dev.bwmp.holopanels.render;

import dev.bwmp.holopanels.api.ClickType;
import dev.bwmp.holopanels.api.PanelEntry;
import dev.bwmp.holopanels.model.ActionDefinition;

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
