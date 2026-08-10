package dev.aether.holopanels.runtime;

import dev.aether.holopanels.api.ContentProvider;
import dev.aether.holopanels.api.ContentRequest;
import dev.aether.holopanels.api.EntryProvider;
import dev.aether.holopanels.api.EntryRequest;
import dev.aether.holopanels.api.PanelEntry;
import dev.aether.holopanels.model.BoardDefinition;
import dev.aether.holopanels.model.PanelDefinition;
import dev.aether.holopanels.model.StaticEntryDefinition;
import dev.aether.holopanels.model.ViewDefinition;
import dev.aether.holopanels.text.TemplateService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.logging.Level;

public final class ContentService {
    private final JavaPlugin plugin;
    private final ExtensionRegistry extensions;
    private final TemplateService templates;
    private final int maxEntries;
    private final BiConsumer<Player, org.bukkit.NamespacedKey> refresh;
    private final dev.bwmp.keystone.scheduler.KeystoneScheduler scheduler;

    public ContentService(
            JavaPlugin plugin,
            dev.bwmp.keystone.scheduler.KeystoneScheduler scheduler,
            ExtensionRegistry extensions,
            TemplateService templates,
            int maxEntries,
            BiConsumer<Player, org.bukkit.NamespacedKey> refresh
    ) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.extensions = extensions;
        this.templates = templates;
        this.maxEntries = maxEntries;
        this.refresh = refresh;
    }

    public List<PanelEntry> entries(
            Player player,
            BoardDefinition board,
            ViewDefinition view,
            PanelDefinition panel,
            ViewerSession session
    ) {
        if (panel.entryProvider().isEmpty()) {
            List<PanelEntry> entries = staticEntries(player, board, view, panel, session);
            session.entries(panel.id(), entries);
            return entries;
        }

        Optional<List<PanelEntry>> cached = session.entries(panel.id());
        if (cached.isPresent()) {
            return cached.get();
        }

        Optional<EntryProvider> provider = extensions.entryProvider(panel.entryProvider().get());
        if (provider.isEmpty()) {
            return List.of();
        }

        String requestKey = "entries:" + view.id() + ":" + panel.id();
        if (!session.beginRequest(requestKey)) {
            return List.of();
        }
        int request = session.nextRequest(requestKey);
        try {
            CompletionStage<List<PanelEntry>> stage = provider.get().entries(new EntryRequest(
                    player, board.id(), view.id(), panel.id(), session.state()));
            stage.whenComplete((value, error) -> onMain(() -> {
                if (!player.isOnline() || !session.currentRequest(requestKey, request)) {
                    session.finishRequest(requestKey);
                    return;
                }
                session.finishRequest(requestKey);
                if (error != null) {
                    plugin.getLogger().log(Level.SEVERE, "Entry provider " + panel.entryProvider().get()
                            + " failed for " + board.id() + "/" + panel.id(), error);
                    session.entries(panel.id(), List.of());
                } else {
                    List<PanelEntry> safe = value == null ? List.of() : List.copyOf(value);
                    if (safe.size() > maxEntries) {
                        plugin.getLogger().warning("Entry provider " + panel.entryProvider().get() + " returned "
                                + safe.size() + " entries; truncating to " + maxEntries + ".");
                        safe = safe.subList(0, maxEntries);
                    }
                    session.entries(panel.id(), safe);
                }
                refresh.accept(player, board.id());
            }));
        } catch (RuntimeException exception) {
            session.finishRequest(requestKey);
            plugin.getLogger().log(Level.SEVERE, "Entry provider " + panel.entryProvider().get()
                    + " threw before returning a result", exception);
            session.entries(panel.id(), List.of());
        }
        return List.of();
    }

    public Optional<List<Component>> content(
            Player player,
            BoardDefinition board,
            ViewDefinition view,
            PanelDefinition panel,
            ViewerSession session,
            Optional<PanelEntry> selected
    ) {
        if (panel.contentProvider().isEmpty()) {
            return Optional.empty();
        }
        String cacheKey = "content:" + view.id() + ":" + panel.id();
        Optional<List<PanelEntry>> cached = session.entries(cacheKey);
        if (cached.isPresent()) {
            return Optional.of(cached.get().stream().map(PanelEntry::label).toList());
        }

        Optional<ContentProvider> provider = extensions.contentProvider(panel.contentProvider().get());
        if (provider.isEmpty()) {
            return Optional.of(List.of());
        }
        if (!session.beginRequest(cacheKey)) {
            return Optional.of(List.of());
        }
        int request = session.nextRequest(cacheKey);
        try {
            provider.get().content(new ContentRequest(
                    player, board.id(), view.id(), panel.id(), selected, session.state()
            )).whenComplete((value, error) -> onMain(() -> {
                if (!player.isOnline() || !session.currentRequest(cacheKey, request)) {
                    session.finishRequest(cacheKey);
                    return;
                }
                session.finishRequest(cacheKey);
                if (error != null) {
                    plugin.getLogger().log(Level.SEVERE, "Content provider " + panel.contentProvider().get()
                            + " failed for " + board.id() + "/" + panel.id(), error);
                    session.entries(cacheKey, List.of());
                } else {
                    List<Component> safe = value == null ? List.of() : List.copyOf(value);
                    List<PanelEntry> wrapped = new ArrayList<>(safe.size());
                    for (int index = 0; index < safe.size(); index++) {
                        wrapped.add(PanelEntry.builder("line-" + index, safe.get(index)).build());
                    }
                    session.entries(cacheKey, wrapped);
                }
                refresh.accept(player, board.id());
            }));
        } catch (RuntimeException exception) {
            session.finishRequest(cacheKey);
            plugin.getLogger().log(Level.SEVERE, "Content provider " + panel.contentProvider().get()
                    + " threw before returning a result", exception);
            session.entries(cacheKey, List.of());
        }
        return Optional.of(List.of());
    }

    private List<PanelEntry> staticEntries(
            Player player,
            BoardDefinition board,
            ViewDefinition view,
            PanelDefinition panel,
            ViewerSession session
    ) {
        List<PanelEntry> result = new ArrayList<>();
        for (StaticEntryDefinition definition : panel.staticEntries()) {
            PanelEntry.Builder builder = PanelEntry.builder(definition.id(),
                    templates.render(definition.label(), player, board.id(), view.id(), Optional.empty(), session.state()));
            definition.fields().forEach((name, value) -> builder.field(name,
                    templates.render(value, player, board.id(), view.id(), Optional.empty(), session.state())));
            definition.attributes().forEach(builder::attribute);
            result.add(builder.build());
        }
        return List.copyOf(result);
    }

    private void onMain(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            scheduler.run(runnable);
        }
    }
}
