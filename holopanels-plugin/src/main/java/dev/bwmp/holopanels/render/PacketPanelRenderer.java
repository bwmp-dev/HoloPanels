package dev.bwmp.holopanels.render;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import dev.bwmp.holopanels.model.PanelStyle;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public final class PacketPanelRenderer {
    private static final AtomicInteger ENTITY_IDS = new AtomicInteger(1_850_000_000);

    private record VisiblePanel(int[] entityIds, RenderedPanel panel) {
    }

    private final TextDisplayMetadataSchema schema;
    private final Map<UUID, Map<String, VisiblePanel>> visible = new ConcurrentHashMap<>();
    private final Map<UUID, String> hovered = new ConcurrentHashMap<>();

    public PacketPanelRenderer(TextDisplayMetadataSchema schema) {
        this.schema = schema;
    }

    public void apply(Player player, List<RenderedPanel> panels) {
        Map<String, VisiblePanel> current = visible.computeIfAbsent(player.getUniqueId(), ignored -> new LinkedHashMap<>());
        Map<String, RenderedPanel> desired = new LinkedHashMap<>();
        for (RenderedPanel panel : panels) {
            desired.put(panel.renderId(), panel);
        }

        List<String> removed = current.keySet().stream().filter(id -> !desired.containsKey(id)).toList();
        for (String id : removed) {
            destroy(player, current.remove(id));
        }

        for (Map.Entry<String, RenderedPanel> entry : desired.entrySet()) {
            VisiblePanel existing = current.get(entry.getKey());
            RenderedPanel panel = entry.getValue();
            if (existing == null || !samePlacement(existing.panel(), panel)) {
                if (existing != null) {
                    destroy(player, existing);
                }
                current.put(entry.getKey(), spawn(player, panel));
            } else if (!existing.panel().equals(panel)) {
                sendMetadata(player, existing.entityIds(), panel);
                current.put(entry.getKey(), new VisiblePanel(existing.entityIds(), panel));
            }
        }

        if (current.isEmpty()) {
            visible.remove(player.getUniqueId());
        }
        forgetHoverIfGone(player.getUniqueId(), current);
    }

    private VisiblePanel spawn(Player player, RenderedPanel panel) {
        int[] entityIds = new int[panel.layers().size()];
        for (int index = 0; index < entityIds.length; index++) {
            entityIds[index] = ENTITY_IDS.incrementAndGet();
            sendSpawn(player, entityIds[index], panel.layers().get(index).location());
        }
        sendMetadata(player, entityIds, panel);
        return new VisiblePanel(entityIds, panel);
    }

    private void destroy(Player player, VisiblePanel panel) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                new WrapperPlayServerDestroyEntities(panel.entityIds()));
    }

    /**
     * Whether the entities already sent still stand where this panel wants
     * them. A panel that has grown a layer, or moved one, is respawned rather
     * than patched: metadata can neither move an entity nor conjure one.
     */
    private boolean samePlacement(RenderedPanel left, RenderedPanel right) {
        if (left.layers().size() != right.layers().size()) {
            return false;
        }
        for (int index = 0; index < left.layers().size(); index++) {
            if (!sameLocation(left.layers().get(index).location(), right.layers().get(index).location())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Marks {@code renderId} as the panel the player is aiming at, or nothing
     * when {@code renderId} is null, redrawing only the panels whose background
     * that changes. Called far more often than {@link #apply}, so it does no
     * layout work of its own.
     */
    public void hover(Player player, String renderId) {
        UUID uuid = player.getUniqueId();
        String previous = renderId == null ? hovered.remove(uuid) : hovered.put(uuid, renderId);
        if (Objects.equals(previous, renderId)) {
            return;
        }
        Map<String, VisiblePanel> panels = visible.get(uuid);
        if (panels == null) {
            return;
        }
        resendBackground(player, panels, previous);
        resendBackground(player, panels, renderId);
    }

    private void resendBackground(Player player, Map<String, VisiblePanel> panels, String renderId) {
        VisiblePanel panel = renderId == null ? null : panels.get(renderId);
        if (panel != null) {
            sendMetadata(player, panel.entityIds(), panel.panel());
        }
    }

    private void forgetHoverIfGone(UUID uuid, Map<String, VisiblePanel> panels) {
        String renderId = hovered.get(uuid);
        if (renderId != null && !panels.containsKey(renderId)) {
            hovered.remove(uuid);
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
            destroy(player, panels.remove(id));
        }
        if (panels.isEmpty()) {
            visible.remove(player.getUniqueId());
        }
        forgetHoverIfGone(player.getUniqueId(), panels);
    }

    public void hideAll(Player player) {
        Map<String, VisiblePanel> panels = visible.remove(player.getUniqueId());
        hovered.remove(player.getUniqueId());
        if (panels == null || panels.isEmpty()) {
            return;
        }
        int[] ids = panels.values().stream()
                .flatMapToInt(panel -> IntStream.of(panel.entityIds()))
                .toArray();
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

    private void sendMetadata(Player player, int[] entityIds, RenderedPanel panel) {
        PanelStyle style = panel.style();
        for (int index = 0; index < entityIds.length; index++) {
            PanelLayer layer = panel.layers().get(index);
            float scale = (float) layer.scale();
            List<EntityData<?>> metadata = new ArrayList<>();
            metadata.add(new EntityData<>(schema.scale(), EntityDataTypes.VECTOR3F, new Vector3f(scale, scale, scale)));
            metadata.add(new EntityData<>(schema.billboard(), EntityDataTypes.BYTE, billboard(style.billboard())));
            metadata.add(new EntityData<>(schema.text(), EntityDataTypes.ADV_COMPONENT, layer.text()));
            metadata.add(new EntityData<>(schema.lineWidth(), EntityDataTypes.INT, layer.lineWidth()));
            metadata.add(new EntityData<>(schema.backgroundColor(), EntityDataTypes.INT, background(player, panel, layer)));
            metadata.add(new EntityData<>(schema.textOpacity(), EntityDataTypes.BYTE, (byte) style.textOpacity()));
            metadata.add(new EntityData<>(schema.flags(), EntityDataTypes.BYTE, flags(style)));
            PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                    new WrapperPlayServerEntityMetadata(entityIds[index], metadata));
        }
    }

    /** Transparent for the layers drawing text in front of a backdrop that has the background already. */
    private int background(Player player, RenderedPanel panel, PanelLayer layer) {
        PanelStyle style = panel.style();
        if (!layer.carriesBackground()) {
            return 0;
        }
        if (style.hoverBackgroundColor().isEmpty() || !panel.renderId().equals(hovered.get(player.getUniqueId()))) {
            return style.backgroundColor();
        }
        return style.hoverBackgroundColor().getAsInt();
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

}
