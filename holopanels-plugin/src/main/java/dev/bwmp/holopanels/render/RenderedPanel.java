package dev.bwmp.holopanels.render;

import dev.bwmp.holopanels.model.PanelStyle;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;

import java.util.List;

/**
 * A panel as it is about to be drawn: the box it occupies, the display entities
 * that make it up, and which line of it sits where.
 */
public record RenderedPanel(
        NamespacedKey boardId,
        NamespacedKey viewId,
        String panelId,
        Location location,
        double width,
        double height,
        PanelStyle style,
        List<PanelLayer> layers,
        List<PanelStack.Band> bands,
        List<ClickRegion> clickRegions
) {
    public RenderedPanel {
        location = location.clone();
        layers = List.copyOf(layers);
        bands = List.copyOf(bands);
        clickRegions = List.copyOf(clickRegions);
    }

    public String renderId() {
        return boardId + "/" + viewId + "/" + panelId;
    }

    public Location hitCenter() {
        return location.clone().add(0.0, height / 2.0 + style.clickOffsetY(), 0.0);
    }

    /**
     * The line at a hit, or -1 for a hit inside the box but past the end of the
     * text — which a sized panel has plenty of, and which no click region owns.
     */
    public int lineAt(double verticalOffset) {
        double fromTop = height / 2.0 - verticalOffset;
        for (PanelStack.Band band : bands) {
            if (fromTop >= band.fromTop() && fromTop < band.toTop()) {
                return band.line();
            }
        }
        return -1;
    }
}
