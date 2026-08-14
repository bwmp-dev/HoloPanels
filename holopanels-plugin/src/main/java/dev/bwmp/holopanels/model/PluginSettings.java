package dev.bwmp.holopanels.model;

public record PluginSettings(
        int visibilityCheckTicks,
        int placeholderRefreshTicks,
        double movementThreshold,
        double defaultVisibilityDistance,
        double defaultClickDistance,
        int maxPanelsPerView,
        int maxLinesPerPanel,
        int maxEntriesPerProvider,
        int maxActionsPerChain,
        PanelStyle defaultStyle
) {
}
