package dev.bwmp.holopanels.runtime;

import dev.bwmp.holopanels.model.BoardDefinition;
import dev.bwmp.holopanels.model.ConfigSnapshot;
import dev.bwmp.holopanels.render.PacketPanelRenderer;
import dev.bwmp.holopanels.render.PanelGeometry;
import dev.bwmp.holopanels.render.RenderedPanel;
import dev.bwmp.keystone.scheduler.KeystoneScheduler;
import dev.bwmp.keystone.scheduler.KeystoneTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Tracks which panel each viewer is aiming at so panels that define a hover
 * background can light up under the crosshair.
 * <p>
 * Deliberately separate from the visibility loop: this runs several times a
 * second to feel responsive, and must stay cheap enough to justify that. It
 * only ever re-sends metadata for the two panels a change affects, and skips
 * players with no hover-capable panel in front of them.
 */
public final class HoverService {
    private record Hovered(String renderId, double distance) {
    }

    private final KeystoneScheduler scheduler;
    private final Supplier<ConfigSnapshot> snapshot;
    private final PacketPanelRenderer renderer;
    private KeystoneTask task;

    public HoverService(KeystoneScheduler scheduler, Supplier<ConfigSnapshot> snapshot, PacketPanelRenderer renderer) {
        this.scheduler = scheduler;
        this.snapshot = snapshot;
        this.renderer = renderer;
    }

    public void start() {
        stop();
        int ticks = snapshot.get().settings().hoverCheckTicks();
        task = scheduler.runTimer(this::updateAll, 1L, ticks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ServerThreads.atPlayer(scheduler, player, () -> update(player));
        }
    }

    private void update(Player player) {
        ConfigSnapshot current = snapshot.get();
        Location eye = player.getEyeLocation();
        String hovered = renderer.panels(player).stream()
                .filter(panel -> panel.style().hoverBackgroundColor().isPresent())
                .map(panel -> hit(eye, panel, current.boards().get(panel.boardId())))
                .flatMap(Optional::stream)
                .min(Comparator.comparingDouble(Hovered::distance))
                .map(Hovered::renderId)
                .orElse(null);
        renderer.hover(player, hovered);
    }

    private Optional<Hovered> hit(Location eye, RenderedPanel panel, BoardDefinition board) {
        if (board == null) {
            return Optional.empty();
        }
        return PanelGeometry.hitCentered(
                        eye, panel.hitCenter(), panel.width(),
                        panel.height(), board.clickDistance())
                .map(hit -> new Hovered(panel.renderId(), hit.distance()));
    }
}
