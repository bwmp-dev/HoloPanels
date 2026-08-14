package dev.bwmp.holopanels.render;

import dev.bwmp.keystone.compat.McVersion;

/**
 * Which metadata index each Text Display field is sent under.
 * <p>
 * These are wire indices, not API constants, so they move when the protocol
 * moves and nothing at compile time notices. 1.20.2 inserted a second
 * interpolation duration into the Display entity's metadata, pushing every
 * field after it — including all five text fields — up by one. Sending the
 * newer layout to an older server does not error; it writes the text into the
 * slot that used to be the glow colour override and the panel comes out blank.
 */
public record TextDisplayMetadataSchema(
        int billboard,
        int text,
        int lineWidth,
        int backgroundColor,
        int textOpacity,
        int flags
) {
    private static final McVersion INTERPOLATION_SPLIT = McVersion.of(1, 20, 2);

    private static final TextDisplayMetadataSchema LEGACY =
            new TextDisplayMetadataSchema(14, 22, 23, 24, 25, 26);

    private static final TextDisplayMetadataSchema CURRENT =
            new TextDisplayMetadataSchema(15, 23, 24, 25, 26, 27);

    /**
     * An unparseable version reads as current rather than legacy: a version
     * Keystone cannot parse is far more likely to be one newer than anything
     * known than a 1.19.4 in disguise.
     */
    public static TextDisplayMetadataSchema forVersion(McVersion version) {
        return version.isKnown() && version.below(INTERPOLATION_SPLIT) ? LEGACY : CURRENT;
    }

    public boolean isLegacy() {
        return equals(LEGACY);
    }
}
