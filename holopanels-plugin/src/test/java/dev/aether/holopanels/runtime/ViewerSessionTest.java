package dev.aether.holopanels.runtime;

import dev.aether.holopanels.api.PanelEntry;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewerSessionTest {
    @Test
    void selectionResolvesAgainstCurrentEntries() {
        ViewerSession session = new ViewerSession(NamespacedKey.minecraft("root"));
        PanelEntry entry = PanelEntry.builder("first", Component.text("First")).build();
        session.entries("menu", List.of(entry));
        session.select("menu", "first");

        assertEquals(entry, session.selectedEntry("menu").orElseThrow());
    }

    @Test
    void requestCoalescingEndsWhenRequestFinishes() {
        ViewerSession session = new ViewerSession(NamespacedKey.minecraft("root"));

        assertTrue(session.beginRequest("provider"));
        assertFalse(session.beginRequest("provider"));
        session.finishRequest("provider");
        assertTrue(session.beginRequest("provider"));
    }

    @Test
    void resetClearsNavigationAndHiddenState() {
        ViewerSession session = new ViewerSession(NamespacedKey.minecraft("root"));
        session.currentView(NamespacedKey.minecraft("other"));
        session.state("filter", "owned");
        session.hidden(true);

        session.reset(NamespacedKey.minecraft("root"));

        assertEquals(NamespacedKey.minecraft("root"), session.currentView());
        assertTrue(session.state().isEmpty());
        assertFalse(session.hidden());
    }
}
