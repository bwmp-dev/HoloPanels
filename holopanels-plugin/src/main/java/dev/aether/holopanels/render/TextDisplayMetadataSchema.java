package dev.aether.holopanels.render;

public record TextDisplayMetadataSchema(
        int billboard,
        int text,
        int lineWidth,
        int backgroundColor,
        int textOpacity,
        int flags
) {
    public static TextDisplayMetadataSchema current() {
        return new TextDisplayMetadataSchema(15, 23, 24, 25, 26, 27);
    }
}
