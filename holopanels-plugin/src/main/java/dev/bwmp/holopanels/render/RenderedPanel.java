package dev.bwmp.holopanels.render;

import dev.bwmp.holopanels.model.PanelStyle;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;

import java.util.List;

public record RenderedPanel(
        NamespacedKey boardId,
        NamespacedKey viewId,
        String panelId,
        Location location,
        Component text,
        int lineCount,
        PanelStyle style,
        List<ClickRegion> clickRegions
) {
    public RenderedPanel {
        location = location.clone();
        clickRegions = List.copyOf(clickRegions);
    }

    public String renderId() {
        return boardId + "/" + viewId + "/" + panelId;
    }

    public double height() {
        return Math.max(1, lineCount) * style.lineHeight();
    }

    public Location hitCenter() {
        return location.clone().add(0.0, height() / 2.0 + style.clickOffsetY(), 0.0);
    }

    public int lineAt(double verticalOffset) {
        return (int) Math.floor((height() / 2.0 - verticalOffset) / style.lineHeight());
    }
}
