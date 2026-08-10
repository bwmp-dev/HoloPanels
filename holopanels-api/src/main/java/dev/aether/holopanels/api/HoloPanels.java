package dev.aether.holopanels.api;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public interface HoloPanels {
    Registration registerEntryProvider(Plugin owner, NamespacedKey id, EntryProvider provider);

    Registration registerContentProvider(Plugin owner, NamespacedKey id, ContentProvider provider);

    Registration registerCondition(Plugin owner, NamespacedKey id, ConditionEvaluator evaluator);

    Registration registerAction(Plugin owner, NamespacedKey id, ActionHandler handler);

    void refresh(NamespacedKey boardId);

    void refresh(Player player, NamespacedKey boardId);

    boolean open(Player player, NamespacedKey boardId, NamespacedKey viewId);

    boolean reset(Player player, NamespacedKey boardId);

    void hide(Player player, NamespacedKey boardId);
}
