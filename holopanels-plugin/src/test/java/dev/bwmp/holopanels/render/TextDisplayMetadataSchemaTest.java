package dev.bwmp.holopanels.render;

import dev.bwmp.keystone.compat.McVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextDisplayMetadataSchemaTest {
    @Test
    void textDisplaysBeforeTheInterpolationSplitUseTheLowerIndices() {
        TextDisplayMetadataSchema schema = TextDisplayMetadataSchema.forVersion(McVersion.parse("1.19.4-R0.1-SNAPSHOT"));

        assertTrue(schema.isLegacy());
        assertEquals(11, schema.scale());
        assertEquals(14, schema.billboard());
        assertEquals(22, schema.text());
        assertEquals(26, schema.flags());
    }

    @Test
    void oneTwentyOneIsStillLegacy() {
        assertTrue(TextDisplayMetadataSchema.forVersion(McVersion.parse("1.20.1")).isLegacy());
    }

    @Test
    void everythingFromTheSplitOnwardsShiftsUpByOne() {
        TextDisplayMetadataSchema schema = TextDisplayMetadataSchema.forVersion(McVersion.parse("1.20.2"));

        assertFalse(schema.isLegacy());
        assertEquals(12, schema.scale());
        assertEquals(15, schema.billboard());
        assertEquals(23, schema.text());
        assertEquals(27, schema.flags());
    }

    @Test
    void theTwentySixVersionSchemeSortsAboveTheSplit() {
        assertFalse(TextDisplayMetadataSchema.forVersion(McVersion.parse("26.2.build.2619-stable")).isLegacy());
    }

    @Test
    void anUnreadableVersionIsAssumedCurrentRatherThanLegacy() {
        assertFalse(TextDisplayMetadataSchema.forVersion(McVersion.parse("banana")).isLegacy());
    }
}
