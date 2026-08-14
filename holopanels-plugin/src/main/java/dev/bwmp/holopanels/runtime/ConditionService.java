package dev.bwmp.holopanels.runtime;

import dev.bwmp.holopanels.api.ConditionContext;
import dev.bwmp.holopanels.api.ConditionEvaluator;
import dev.bwmp.holopanels.api.PanelEntry;
import dev.bwmp.holopanels.model.BoardDefinition;
import dev.bwmp.holopanels.model.ConditionDefinition;
import dev.bwmp.holopanels.model.ViewDefinition;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Optional;

public final class ConditionService {
    private final JavaPlugin plugin;
    private final ExtensionRegistry extensions;

    public ConditionService(JavaPlugin plugin, ExtensionRegistry extensions) {
        this.plugin = plugin;
        this.extensions = extensions;
    }

    public boolean test(
            ConditionDefinition condition,
            Player player,
            BoardDefinition board,
            ViewDefinition view,
            String panelId,
            ViewerSession session,
            Optional<PanelEntry> selected
    ) {
        return switch (condition.kind()) {
            case ALWAYS -> true;
            case ALL -> condition.children().stream().allMatch(child -> test(child, player, board, view, panelId, session, selected));
            case ANY -> condition.children().stream().anyMatch(child -> test(child, player, board, view, panelId, session, selected));
            case NOT -> condition.children().size() == 1
                    && !test(condition.children().get(0), player, board, view, panelId, session, selected);
            case PERMISSION -> player.hasPermission(condition.arguments().getOrDefault("permission", ""));
            case SELECTED -> session.selection(condition.arguments().getOrDefault("panel", "")).isPresent();
            case ENTRY_ATTRIBUTE -> selected.map(entry -> entry.attribute(condition.arguments().getOrDefault("name", ""))
                    .equalsIgnoreCase(condition.arguments().getOrDefault("equals", "true"))).orElse(false);
            case SESSION_STATE -> session.state().getOrDefault(condition.arguments().getOrDefault("name", ""), "")
                    .equalsIgnoreCase(condition.arguments().getOrDefault("equals", "true"));
            case CUSTOM -> custom(condition, player, board, view, panelId, session, selected);
        };
    }

    private boolean custom(
            ConditionDefinition condition,
            Player player,
            BoardDefinition board,
            ViewDefinition view,
            String panelId,
            ViewerSession session,
            Optional<PanelEntry> selected
    ) {
        NamespacedKey id = NamespacedKey.fromString(condition.arguments().getOrDefault("id", ""), plugin);
        if (id == null) {
            return false;
        }
        Optional<ConditionEvaluator> evaluator = extensions.condition(id);
        if (evaluator.isEmpty()) {
            return false;
        }
        Map<String, String> arguments = condition.arguments().entrySet().stream()
                .filter(entry -> !entry.getKey().equals("id"))
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        return evaluator.get().test(new ConditionContext(
                player, board.id(), view.id(), panelId, selected, arguments, session.state()));
    }
}
