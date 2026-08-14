package dev.bwmp.holopanels.text;

import dev.bwmp.holopanels.api.PanelEntry;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TemplateService {
    private static final Pattern TOKEN = Pattern.compile("<(entry|state|player|board|view):([a-zA-Z0-9_.-]+)>");

    private final PluginManager pluginManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public TemplateService(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    public Component render(
            String template,
            Player player,
            NamespacedKey boardId,
            NamespacedKey viewId,
            Optional<PanelEntry> entry,
            Map<String, String> state
    ) {
        String expanded = template.replace("\\n", "\n");
        if (pluginManager.isPluginEnabled("PlaceholderAPI")) {
            expanded = PlaceholderAPI.setPlaceholders(player, expanded);
        }

        Component result = Component.empty();
        Matcher matcher = TOKEN.matcher(expanded);
        int cursor = 0;
        while (matcher.find()) {
            result = result.append(miniMessage.deserialize(expanded.substring(cursor, matcher.start())));
            result = result.append(resolve(matcher.group(1), matcher.group(2), player, boardId, viewId, entry, state));
            cursor = matcher.end();
        }
        return result.append(miniMessage.deserialize(expanded.substring(cursor)));
    }

    private Component resolve(
            String namespace,
            String name,
            Player player,
            NamespacedKey boardId,
            NamespacedKey viewId,
            Optional<PanelEntry> entry,
            Map<String, String> state
    ) {
        return switch (namespace) {
            case "entry" -> entry.map(value -> value.field(name)).orElse(Component.empty());
            case "state" -> Component.text(state.getOrDefault(name, ""));
            case "player" -> name.equals("name") ? Component.text(player.getName())
                    : name.equals("uuid") ? Component.text(player.getUniqueId().toString()) : Component.empty();
            case "board" -> name.equals("id") ? Component.text(boardId.toString()) : Component.empty();
            case "view" -> name.equals("id") ? Component.text(viewId.toString()) : Component.empty();
            default -> Component.empty();
        };
    }
}
