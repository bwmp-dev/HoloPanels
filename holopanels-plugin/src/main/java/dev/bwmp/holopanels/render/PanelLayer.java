package dev.bwmp.holopanels.render;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;

/**
 * One display entity's worth of a panel: either the backdrop that draws the
 * box, or a run of lines at one scale sitting in front of it.
 */
public record PanelLayer(Location location, Component text, double scale, int lineWidth, boolean carriesBackground) {
    public PanelLayer {
        location = location.clone();
    }
}
