package dev.bwmp.holopanels.model;

import java.util.OptionalDouble;

/**
 * One line of a panel, and how big it is drawn.
 * <p>
 * A line with its own scale becomes its own display entity, because a text
 * display can only be one size. Consecutive lines that agree share one.
 */
public record PanelLine(String template, OptionalDouble scale) {
    public static PanelLine of(String template) {
        return new PanelLine(template, OptionalDouble.empty());
    }

    public double scaleOr(double fallback) {
        return scale.orElse(fallback);
    }
}
