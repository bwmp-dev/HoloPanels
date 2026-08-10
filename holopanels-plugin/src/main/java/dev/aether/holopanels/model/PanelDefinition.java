package dev.aether.holopanels.model;

import dev.aether.holopanels.api.ClickType;
import org.bukkit.NamespacedKey;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record PanelDefinition(
        String id,
        PanelType type,
        PanelOffset offset,
        PanelStyle style,
        ConditionDefinition condition,
        Optional<NamespacedKey> entryProvider,
        Optional<NamespacedKey> contentProvider,
        Optional<String> selectionPanel,
        List<StaticEntryDefinition> staticEntries,
        List<String> header,
        List<String> lines,
        String rowTemplate,
        String selectedRowTemplate,
        String emptyText,
        int pageSize,
        List<ButtonDefinition> buttons,
        Map<ClickType, List<ActionDefinition>> clicks
) {
    public PanelDefinition {
        staticEntries = List.copyOf(staticEntries);
        header = List.copyOf(header);
        lines = List.copyOf(lines);
        buttons = List.copyOf(buttons);
        clicks = Map.copyOf(clicks);
    }
}
