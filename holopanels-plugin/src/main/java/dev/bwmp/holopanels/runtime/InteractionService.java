package dev.bwmp.holopanels.runtime;

import dev.bwmp.holopanels.api.ClickType;
import dev.bwmp.holopanels.model.BoardDefinition;
import dev.bwmp.holopanels.model.ConfigSnapshot;
import dev.bwmp.holopanels.model.ViewDefinition;
import dev.bwmp.holopanels.render.ClickRegion;
import dev.bwmp.holopanels.render.PacketPanelRenderer;
import dev.bwmp.holopanels.render.PanelGeometry;
import dev.bwmp.holopanels.render.RenderedPanel;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class InteractionService implements Listener {
    private record Target(RenderedPanel panel, ClickRegion region, double distance) {
    }

    private final Supplier<ConfigSnapshot> snapshot;
    private final SessionManager sessions;
    private final PacketPanelRenderer renderer;
    private final ActionService actions;

    public InteractionService(
            Supplier<ConfigSnapshot> snapshot,
            SessionManager sessions,
            PacketPanelRenderer renderer,
            ActionService actions
    ) {
        this.snapshot = snapshot;
        this.sessions = sessions;
        this.renderer = renderer;
        this.actions = actions;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ClickType clickType = clickType(event);
        if (clickType == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("holopanels.use")) {
            return;
        }

        Optional<Target> target = target(player, clickType);
        if (target.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Target hit = target.get();
        ConfigSnapshot current = snapshot.get();
        BoardDefinition board = current.boards().get(hit.panel().boardId());
        ViewDefinition view = current.views().get(hit.panel().viewId());
        if (board == null || view == null) {
            return;
        }
        ViewerSession session = sessions.get(player.getUniqueId(), board);
        actions.execute(player, board, view, hit.panel().panelId(), hit.region(), clickType, session);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (target(event.getPlayer(), event.getPlayer().isSneaking() ? ClickType.SHIFT_LEFT : ClickType.LEFT).isPresent()) {
            event.setCancelled(true);
        }
    }

    private Optional<Target> target(Player player, ClickType clickType) {
        ConfigSnapshot current = snapshot.get();
        return renderer.panels(player).stream()
                .map(panel -> hit(player, panel, current.boards().get(panel.boardId()), clickType))
                .flatMap(Optional::stream)
                .min(Comparator.comparingDouble(Target::distance));
    }

    private Optional<Target> hit(Player player, RenderedPanel panel, BoardDefinition board, ClickType clickType) {
        if (board == null || panel.clickRegions().isEmpty()) {
            return Optional.empty();
        }
        return PanelGeometry.hitCentered(
                        player.getEyeLocation(), panel.hitCenter(), panel.width(),
                        panel.height(), board.clickDistance())
                .flatMap(hit -> {
                    int line = panel.lineAt(hit.vertical());
                    return panel.clickRegions().stream()
                            .filter(region -> region.contains(line) && !region.actions().getOrDefault(clickType, List.of()).isEmpty())
                            .findFirst()
                            .map(region -> new Target(panel, region, hit.distance()));
                });
    }

    private ClickType clickType(PlayerInteractEvent event) {
        boolean left = event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK;
        boolean right = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (!left && !right) {
            return null;
        }
        if (left) {
            return event.getPlayer().isSneaking() ? ClickType.SHIFT_LEFT : ClickType.LEFT;
        }
        return event.getPlayer().isSneaking() ? ClickType.SHIFT_RIGHT : ClickType.RIGHT;
    }
}
