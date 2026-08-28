package dev.bwmp.holopanels.render;

import dev.bwmp.holopanels.model.PanelSize;
import dev.bwmp.holopanels.model.PanelStyle;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RenderedPanelTest {

    private static PanelStyle style(Optional<PanelSize> size) {
        return new PanelStyle(0.25, 1.0, size, 0.0, 4.0, 200, 0, OptionalInt.empty(), 255,
                "left", "fixed", false, false);
    }

    private static RenderedPanel panel(List<Double> scales, PanelStyle style) {
        List<PanelStack.Run> runs = PanelStack.runs(scales, style.lineHeight());
        double height = style.height(PanelStack.height(runs));
        Location location = new Location(null, 0.0, 64.0, 0.0);
        return new RenderedPanel(
                NamespacedKey.minecraft("board"), NamespacedKey.minecraft("view"), "panel",
                location, style.width(style.interactionWidth()), height, style,
                List.of(new PanelLayer(location, Component.empty(), 1.0, 200, true)),
                PanelStack.bands(runs, style.lineHeight()), List.of());
    }

    @Test
    void heightComesFromTheTextWhenNoBoxIsGiven() {
        assertEquals(2.0, panel(List.of(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0), style(Optional.empty())).height());
        assertEquals(4.0, panel(List.of(2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0), style(Optional.empty())).height());
    }

    @Test
    void aDeclaredBoxWinsOverWhatTheTextMeasures() {
        RenderedPanel sized = panel(List.of(1.0, 1.0), style(Optional.of(new PanelSize(5.0, 6.0))));

        assertEquals(5.0, sized.width());
        assertEquals(6.0, sized.height());
        assertEquals(67.0, sized.hitCenter().getY());
    }

    @Test
    void linesAreFoundAtTheirOwnScale() {
        RenderedPanel mixed = panel(List.of(1.0, 2.0, 1.0), style(Optional.empty()));

        // 0.25 + 0.5 + 0.25 tall, so the middle line owns twice the band.
        assertEquals(1.0, mixed.height());
        assertEquals(0, mixed.lineAt(0.4));
        assertEquals(1, mixed.lineAt(0.1));
        assertEquals(1, mixed.lineAt(-0.2));
        assertEquals(2, mixed.lineAt(-0.4));
    }

    @Test
    void aHitPastTheEndOfTheTextBelongsToNoLine() {
        RenderedPanel sized = panel(List.of(1.0, 1.0), style(Optional.of(new PanelSize(5.0, 6.0))));

        assertEquals(0, sized.lineAt(2.9));
        assertEquals(1, sized.lineAt(2.6));
        assertEquals(-1, sized.lineAt(0.0));
        assertEquals(-1, sized.lineAt(-2.9));
    }
}
