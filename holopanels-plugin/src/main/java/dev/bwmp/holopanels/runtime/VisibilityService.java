package dev.bwmp.holopanels.runtime;

import dev.bwmp.holopanels.model.BoardDefinition;
import dev.bwmp.holopanels.model.ConfigSnapshot;
import dev.bwmp.holopanels.model.ViewDefinition;
import dev.bwmp.holopanels.render.LayoutService;
import dev.bwmp.holopanels.render.PacketPanelRenderer;
import dev.bwmp.holopanels.render.RenderedPanel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import dev.bwmp.keystone.scheduler.KeystoneScheduler;
import dev.bwmp.keystone.scheduler.KeystoneTask;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class VisibilityService {
    private final JavaPlugin plugin;
    private final KeystoneScheduler scheduler;
    private final Supplier<ConfigSnapshot> snapshot;
    private final SessionManager sessions;
    private final ConditionService conditions;
    private final LayoutService layout;
    private final PacketPanelRenderer renderer;
    private KeystoneTask task;

    public VisibilityService(
            JavaPlugin plugin,
            KeystoneScheduler scheduler,
            Supplier<ConfigSnapshot> snapshot,
            SessionManager sessions,
            ConditionService conditions,
            LayoutService layout,
            PacketPanelRenderer renderer
    ) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.snapshot = snapshot;
        this.sessions = sessions;
        this.conditions = conditions;
        this.layout = layout;
        this.renderer = renderer;
    }

    public void start() {
        stop();
        int ticks = snapshot.get().settings().visibilityCheckTicks();
        task = scheduler.runTimer(this::refreshAll, 1L, ticks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    /**
     * The one place that reads a player's position and the world around them,
     * which is why it is also the one place that has to care about which thread
     * it is on. Every other caller can invoke this from wherever it happens to
     * be and let it land itself.
     */
    public void refresh(Player player) {
        ServerThreads.atPlayer(scheduler, player, () -> render(player));
    }

    private void render(Player player) {
        ConfigSnapshot current = snapshot.get();
        List<RenderedPanel> panels = new ArrayList<>();
        for (BoardDefinition board : current.boards().values()) {
            Optional<Location> location = board.location().flatMap(value -> value.resolve());
            if (location.isEmpty() || !near(player, location.get(), board.visibilityDistance())) {
                continue;
            }
            ViewerSession session = sessions.get(player.getUniqueId(), board);
            if (session.hidden()) {
                continue;
            }
            ViewDefinition view = current.views().get(session.currentView());
            if (view == null) {
                session.reset(board.rootView());
                view = current.views().get(board.rootView());
            }
            if (view == null || !conditions.test(board.condition(), player, board, view, "", session, Optional.empty())) {
                continue;
            }
            panels.addAll(layout.render(player, board, view, session, location.get()));
        }
        renderer.apply(player, panels);
    }

    public void refresh(Player player, NamespacedKey boardId) {
        refresh(player);
    }

    public void refresh(NamespacedKey boardId) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    public void hide(Player player, NamespacedKey boardId) {
        snapshot.get().boards().get(boardId);
        sessions.find(player.getUniqueId(), boardId).ifPresent(session -> session.hidden(true));
        renderer.hideBoard(player, boardId);
    }

    private boolean near(Player player, Location location, double distance) {
        return player.getWorld().equals(location.getWorld())
                && player.getLocation().distanceSquared(location) <= distance * distance;
    }
}
