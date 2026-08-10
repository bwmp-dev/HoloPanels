package dev.aether.holopanels.model;

public record PanelStyle(
        double lineHeight,
        double clickOffsetY,
        double interactionWidth,
        int lineWidth,
        int backgroundColor,
        int textOpacity,
        String alignment,
        String billboard,
        boolean textShadow,
        boolean seeThrough
) {
}
