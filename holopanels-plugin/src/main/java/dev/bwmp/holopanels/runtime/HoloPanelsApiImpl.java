package dev.bwmp.holopanels.runtime;

import dev.bwmp.holopanels.api.ActionHandler;
import dev.bwmp.holopanels.api.ConditionEvaluator;
import dev.bwmp.holopanels.api.ContentProvider;
import dev.bwmp.holopanels.api.EntryProvider;
import dev.bwmp.holopanels.api.HoloPanels;
import dev.bwmp.holopanels.api.Registration;
import dev.bwmp.holopanels.model.BoardDefinition;
import dev.bwmp.holopanels.model.ConfigSnapshot;
import dev.bwmp.keystone.scheduler.KeystoneScheduler;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.function.Supplier;

public final class HoloPanelsApiImpl implements HoloPanels {
    private final JavaPlugin plugin;
    private final KeystoneScheduler scheduler;
    private final ExtensionRegistry extensions;
    private final SessionManager sessions;
    private final VisibilityService visibility;
    private final Supplier<ConfigSnapshot> snapshot;

    public HoloPanelsApiImpl(
            JavaPlugin plugin,
            KeystoneScheduler scheduler,
            ExtensionRegistry extensions,
            SessionManager sessions,
            VisibilityService visibility,
            Supplier<ConfigSnapshot> snapshot
    ) {
        this.plugin = plugin;
        this.scheduler = scheduler;
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

    /**
     * Folia has no single server thread to require, so there the check is
     * dropped rather than answered wrongly. Nothing behind these methods needs
     * it there: the registries and viewer sessions are concurrent, and a
     * refresh puts itself on the thread that owns the player it redraws.
     */
    private void requireMainThread() {
        if (!scheduler.isFolia() && !Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("HoloPanels API mutations must run on the server thread");
        }
    }
}
