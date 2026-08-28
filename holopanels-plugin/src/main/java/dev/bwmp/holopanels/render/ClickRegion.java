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
    /**
     * A region over the whole panel rather than particular lines, which is what
     * a text panel's own clicks are. It matters once a panel can declare a box
     * bigger than its text: the empty part of that box belongs to no line, and
     * a button whose lower half ignored clicks would be a strange button.
     */
    public static ClickRegion wholePanel(Optional<PanelEntry> entry, Map<ClickType, List<ActionDefinition>> actions) {
        return new ClickRegion(Integer.MIN_VALUE, Integer.MAX_VALUE, entry, actions);
    }

    public boolean contains(int line) {
        return line >= firstLine && line <= lastLine;
    }
}
