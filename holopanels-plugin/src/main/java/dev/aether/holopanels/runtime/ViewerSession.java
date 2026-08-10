package dev.aether.holopanels.runtime;

import dev.aether.holopanels.api.PanelEntry;
import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ViewerSession {
    private NamespacedKey currentView;
    private final Map<String, String> selections = new HashMap<>();
    private final Map<String, Integer> pages = new HashMap<>();
    private final Map<String, String> state = new HashMap<>();
    private final Map<String, List<PanelEntry>> entries = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<String, Integer> requests = new HashMap<>();
    private final Set<String> loading = new HashSet<>();
    private boolean hidden;
    private int revision;

    public ViewerSession(NamespacedKey currentView) {
        this.currentView = currentView;
    }

    public NamespacedKey currentView() {
        return currentView;
    }

    public void currentView(NamespacedKey value) {
        currentView = value;
        selections.clear();
        pages.clear();
        hidden = false;
        revision++;
    }

    public Optional<String> selection(String panelId) {
        return Optional.ofNullable(selections.get(panelId));
    }

    public void select(String panelId, String entryId) {
        selections.put(panelId, entryId);
        revision++;
    }

    public void clearSelection(String panelId) {
        if (selections.remove(panelId) != null) {
            revision++;
        }
    }

    public int page(String panelId) {
        return pages.getOrDefault(panelId, 0);
    }

    public void page(String panelId, int value) {
        pages.put(panelId, Math.max(0, value));
        revision++;
    }

    public Map<String, String> state() {
        return Map.copyOf(state);
    }

    public void state(String key, String value) {
        state.put(key, value);
        revision++;
    }

    public Optional<List<PanelEntry>> entries(String panelId) {
        return Optional.ofNullable(entries.get(panelId));
    }

    public void entries(String panelId, List<PanelEntry> value) {
        entries.put(panelId, List.copyOf(value));
    }

    public void invalidateContent() {
        entries.clear();
        requests.clear();
        loading.clear();
        revision++;
    }

    public Optional<PanelEntry> selectedEntry(String panelId) {
        String selected = selections.get(panelId);
        if (selected == null) {
            return Optional.empty();
        }
        return entries(panelId).orElse(List.of()).stream()
                .filter(entry -> entry.id().equals(selected))
                .findFirst();
    }

    public boolean onCooldown(String key, long now) {
        return cooldowns.getOrDefault(key, 0L) > now;
    }

    public void cooldown(String key, long expiresAt) {
        cooldowns.put(key, expiresAt);
    }

    public int nextRequest(String key) {
        int next = requests.getOrDefault(key, 0) + 1;
        requests.put(key, next);
        return next;
    }

    public boolean currentRequest(String key, int value) {
        return requests.getOrDefault(key, 0) == value;
    }

    public boolean beginRequest(String key) {
        return loading.add(key);
    }

    public void finishRequest(String key) {
        loading.remove(key);
    }

    public boolean hidden() {
        return hidden;
    }

    public void hidden(boolean value) {
        hidden = value;
        revision++;
    }

    public int revision() {
        return revision;
    }

    public void reset(NamespacedKey rootView) {
        currentView = rootView;
        selections.clear();
        pages.clear();
        state.clear();
        entries.clear();
        cooldowns.clear();
        requests.clear();
        loading.clear();
        hidden = false;
        revision++;
    }
}
