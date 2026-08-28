package dev.bwmp.holopanels.model;

import java.util.Optional;
import java.util.OptionalInt;

public record PanelStyle(
        double lineHeight,
        double scale,
        Optional<PanelSize> size,
        double clickOffsetY,
        double interactionWidth,
        int lineWidth,
        int backgroundColor,
        OptionalInt hoverBackgroundColor,
        int textOpacity,
        String alignment,
        String billboard,
        boolean textShadow,
        boolean seeThrough
) {
    /** The box this panel occupies, falling back to what its text works out to. */
    public double width(double textWidth) {
        return size.map(PanelSize::width).orElse(textWidth);
    }

    public double height(double textHeight) {
        return size.map(PanelSize::height).orElse(textHeight);
    }

    /** How tall one line actually comes out once the display is scaled. */
    public double renderedLineHeight() {
        return lineHeight * scale;
    }
}
