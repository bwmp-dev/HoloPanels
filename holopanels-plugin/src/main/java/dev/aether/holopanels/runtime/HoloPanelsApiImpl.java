package dev.aether.holopanels.runtime;

import dev.aether.holopanels.api.ActionHandler;
import dev.aether.holopanels.api.ConditionEvaluator;
import dev.aether.holopanels.api.ContentProvider;
import dev.aether.holopanels.api.EntryProvider;
import dev.aether.holopanels.api.HoloPanels;
import dev.aether.holopanels.api.Registration;
import dev.aether.holopanels.model.BoardDefinition;
import dev.aether.holopanels.model.ConfigSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.function.Supplier;

public final class HoloPanelsApiImpl implements HoloPanels {
    private final JavaPlugin plugin;
    private final ExtensionRegistry extensions;
    private final SessionManager sessions;
    private final VisibilityService visibility;
    private final Supplier<ConfigSnapshot> snapshot;

    public HoloPanelsApiImpl(
            JavaPlugin plugin,
            ExtensionRegistry extensions,
            SessionManager sessions,
            VisibilityService visibility,
            Supplier<ConfigSnapshot> snapshot
    ) {
        this.plugin = plugin;
        this.extensions = extensions;
        this.sessions = sessions;
        this.visibility = visibility;
        this.snapshot = snapshot;
    }

    @Override
    public Registration registerEntryProvider(Plugin owner, NamespacedKey id, EntryProvider provider) {
        requireMainThread();
        return extensions.registerEntryProvider(owner, id, provider);
    }

    @Override
    public Registration registerContentProvider(Plugin owner, NamespacedKey id, ContentProvider provider) {
        requireMainThread();
        return extensions.registerContentProvider(owner, id, provider);
    }

    @Override
    public Registration registerCondition(Plugin owner, NamespacedKey id, ConditionEvaluator evaluator) {
        requireMainThread();
        return extensions.registerCondition(owner, id, evaluator);
    }

    @Override
    public Registration registerAction(Plugin owner, NamespacedKey id, ActionHandler handler) {
        requireMainThread();
        return extensions.registerAction(owner, id, handler);
    }

    @Override
    public void refresh(NamespacedKey boardId) {
        requireMainThread();
        for (Player player : Bukkit.getOnlinePlayers()) {
            sessions.find(player.getUniqueId(), boardId).ifPresent(ViewerSession::invalidateContent);
        }
        visibility.refresh(boardId);
    }

    @Override
    public void refresh(Player player, NamespacedKey boardId) {
        requireMainThread();
        sessions.find(player.getUniqueId(), boardId).ifPresent(ViewerSession::invalidateContent);
        visibility.refresh(player, boardId);
    }

    @Override
    public boolean open(Player player, NamespacedKey boardId, NamespacedKey viewId) {
        requireMainThread();
        BoardDefinition board = snapshot.get().boards().get(boardId);
        if (board == null || !snapshot.get().views().containsKey(viewId)) {
            return false;
        }
        ViewerSession session = sessions.get(player.getUniqueId(), board);
        session.currentView(viewId);
        visibility.refresh(player, boardId);
        return true;
    }

    @Override
    public boolean reset(Player player, NamespacedKey boardId) {
        requireMainThread();
        BoardDefinition board = snapshot.get().boards().get(boardId);
        if (board == null) {
            return false;
        }
        sessions.get(player.getUniqueId(), board).reset(board.rootView());
        visibility.refresh(player, boardId);
        return true;
    }

    @Override
    public void hide(Player player, NamespacedKey boardId) {
        requireMainThread();
        visibility.hide(player, boardId);
    }

    private void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("HoloPanels API mutations must run on the server thread");
        }
    }
}
