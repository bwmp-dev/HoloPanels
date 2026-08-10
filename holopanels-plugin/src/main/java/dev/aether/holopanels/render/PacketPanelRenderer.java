package dev.aether.holopanels.render;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import dev.aether.holopanels.model.PanelStyle;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class PacketPanelRenderer {
    private static final AtomicInteger ENTITY_IDS = new AtomicInteger(1_850_000_000);

    private record VisiblePanel(int entityId, RenderedPanel panel) {
    }

    private final TextDisplayMetadataSchema schema = TextDisplayMetadataSchema.current();
    private final Map<UUID, Map<String, VisiblePanel>> visible = new ConcurrentHashMap<>();

    public void apply(Player player, List<RenderedPanel> panels) {
        Map<String, VisiblePanel> current = visible.computeIfAbsent(player.getUniqueId(), ignored -> new LinkedHashMap<>());
        Map<String, RenderedPanel> desired = new LinkedHashMap<>();
        for (RenderedPanel panel : panels) {
            desired.put(panel.renderId(), panel);
        }

        List<String> removed = current.keySet().stream().filter(id -> !desired.containsKey(id)).toList();
        for (String id : removed) {
            VisiblePanel panel = current.remove(id);
            sendDestroy(player, panel.entityId());
        }

        for (Map.Entry<String, RenderedPanel> entry : desired.entrySet()) {
            VisiblePanel existing = current.get(entry.getKey());
            RenderedPanel panel = entry.getValue();
            if (existing == null) {
                int entityId = ENTITY_IDS.incrementAndGet();
                sendSpawn(player, entityId, panel.location());
                sendMetadata(player, entityId, panel);
                current.put(entry.getKey(), new VisiblePanel(entityId, panel));
                continue;
            }
            if (!sameLocation(existing.panel().location(), panel.location())) {
                sendDestroy(player, existing.entityId());
                int entityId = ENTITY_IDS.incrementAndGet();
                sendSpawn(player, entityId, panel.location());
                sendMetadata(player, entityId, panel);
                current.put(entry.getKey(), new VisiblePanel(entityId, panel));
            } else if (!sameMetadata(existing.panel(), panel)) {
                sendMetadata(player, existing.entityId(), panel);
                current.put(entry.getKey(), new VisiblePanel(existing.entityId(), panel));
            }
        }

        if (current.isEmpty()) {
            visible.remove(player.getUniqueId());
        }
    }

    public List<RenderedPanel> panels(Player player) {
        Map<String, VisiblePanel> panels = visible.get(player.getUniqueId());
        return panels == null ? List.of() : panels.values().stream().map(VisiblePanel::panel).toList();
    }

    public void hideBoard(Player player, org.bukkit.NamespacedKey boardId) {
        Map<String, VisiblePanel> panels = visible.get(player.getUniqueId());
        if (panels == null) {
            return;
        }
        List<String> ids = panels.entrySet().stream()
                .filter(entry -> entry.getValue().panel().boardId().equals(boardId))
                .map(Map.Entry::getKey)
                .toList();
        for (String id : ids) {
            sendDestroy(player, panels.remove(id).entityId());
        }
        if (panels.isEmpty()) {
            visible.remove(player.getUniqueId());
        }
    }

    public void hideAll(Player player) {
        Map<String, VisiblePanel> panels = visible.remove(player.getUniqueId());
        if (panels == null || panels.isEmpty()) {
            return;
        }
        int[] ids = panels.values().stream().mapToInt(VisiblePanel::entityId).toArray();
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerDestroyEntities(ids));
    }

    public void hideAll(Iterable<? extends Player> players) {
        for (Player player : players) {
            hideAll(player);
        }
    }

    private void sendSpawn(Player player, int entityId, Location location) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(UUID.randomUUID()),
                EntityTypes.TEXT_DISPLAY,
                new Vector3d(location.getX(), location.getY(), location.getZ()),
                location.getPitch(),
                location.getYaw(),
                location.getYaw(),
                0,
                Optional.empty()
        ));
    }

    private void sendMetadata(Player player, int entityId, RenderedPanel panel) {
        PanelStyle style = panel.style();
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(schema.billboard(), EntityDataTypes.BYTE, billboard(style.billboard())));
        metadata.add(new EntityData<>(schema.text(), EntityDataTypes.ADV_COMPONENT, panel.text()));
        metadata.add(new EntityData<>(schema.lineWidth(), EntityDataTypes.INT, style.lineWidth()));
        metadata.add(new EntityData<>(schema.backgroundColor(), EntityDataTypes.INT, style.backgroundColor()));
        metadata.add(new EntityData<>(schema.textOpacity(), EntityDataTypes.BYTE, (byte) style.textOpacity()));
        metadata.add(new EntityData<>(schema.flags(), EntityDataTypes.BYTE, flags(style)));
        PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                new WrapperPlayServerEntityMetadata(entityId, metadata));
    }

    private void sendDestroy(Player player, int entityId) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                new WrapperPlayServerDestroyEntities(entityId));
    }

    private byte billboard(String value) {
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "vertical" -> 1;
            case "horizontal" -> 2;
            case "center" -> 3;
            default -> 0;
        };
    }

    private byte flags(PanelStyle style) {
        byte flags = 0;
        if (style.textShadow()) {
            flags |= 0x01;
        }
        if (style.seeThrough()) {
            flags |= 0x02;
        }
        if (style.alignment().equalsIgnoreCase("left")) {
            flags |= 0x08;
        } else if (style.alignment().equalsIgnoreCase("right")) {
            flags |= 0x10;
        }
        return flags;
    }

    private boolean sameLocation(Location left, Location right) {
        return left.getWorld().equals(right.getWorld())
                && left.getX() == right.getX()
                && left.getY() == right.getY()
                && left.getZ() == right.getZ()
                && left.getYaw() == right.getYaw()
                && left.getPitch() == right.getPitch();
    }

    private boolean sameMetadata(RenderedPanel left, RenderedPanel right) {
        return left.text().equals(right.text())
                && left.lineCount() == right.lineCount()
                && left.style().equals(right.style())
                && left.clickRegions().equals(right.clickRegions());
    }
}
