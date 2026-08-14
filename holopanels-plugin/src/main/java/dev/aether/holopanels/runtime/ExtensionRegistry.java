package dev.aether.holopanels.runtime;

import dev.aether.holopanels.api.ActionHandler;
import dev.aether.holopanels.api.ConditionEvaluator;
import dev.aether.holopanels.api.ContentProvider;
import dev.aether.holopanels.api.EntryProvider;
import dev.aether.holopanels.api.Registration;
import dev.bwmp.keystone.registry.OwnedRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * The four extension points third-party plugins can register into.
 * <p>
 * Now a thin facade over Keystone's {@link OwnedRegistry}. What used to live
 * here — four near-identical maps, four near-identical register methods, the
 * namespace check, the idempotent {@code Registration} handle and the bulk
 * unregister — was one generic class's worth of logic written four times. The
 * {@code PluginDisableEvent} listener that drops a departing plugin's entries
 * is now wired centrally by Keystone rather than by hand in the plugin class.
 * <p>
 * The public shape is unchanged on purpose, so none of the services that
 * consume it had to be touched.
 */
public final class ExtensionRegistry {

    private final OwnedRegistry<EntryProvider> entryProviders;
    private final OwnedRegistry<ContentProvider> contentProviders;
    private final OwnedRegistry<ConditionEvaluator> conditions;
    private final OwnedRegistry<ActionHandler> actions;

    public ExtensionRegistry(OwnedRegistry<EntryProvider> entryProviders,
                             OwnedRegistry<ContentProvider> contentProviders,
                             OwnedRegistry<ConditionEvaluator> conditions,
                             OwnedRegistry<ActionHandler> actions) {
        this.entryProviders = entryProviders;
        this.contentProviders = contentProviders;
        this.conditions = conditions;
        this.actions = actions;
    }

    public void onChanged(Runnable callback) {
        entryProviders.onChanged(callback);
        contentProviders.onChanged(callback);
        conditions.onChanged(callback);
        actions.onChanged(callback);
    }

    public Registration registerEntryProvider(Plugin owner, NamespacedKey id, EntryProvider provider) {
        return adapt(entryProviders.register(owner, id, provider));
    }

    public Registration registerContentProvider(Plugin owner, NamespacedKey id, ContentProvider provider) {
        return adapt(contentProviders.register(owner, id, provider));
    }

    public Registration registerCondition(Plugin owner, NamespacedKey id, ConditionEvaluator evaluator) {
        return adapt(conditions.register(owner, id, evaluator));
    }

    public Registration registerAction(Plugin owner, NamespacedKey id, ActionHandler handler) {
        return adapt(actions.register(owner, id, handler));
    }

    public Optional<EntryProvider> entryProvider(NamespacedKey id) {
        return entryProviders.get(id);
    }

    public Optional<ContentProvider> contentProvider(NamespacedKey id) {
        return contentProviders.get(id);
    }

    public Optional<ConditionEvaluator> condition(NamespacedKey id) {
        return conditions.get(id);
    }

    public Optional<ActionHandler> action(NamespacedKey id) {
        return actions.get(id);
    }

    /** Everything third parties have registered, across all four kinds. */
    public int size() {
        return entryProviders.size() + contentProviders.size() + conditions.size() + actions.size();
    }

    public void unregister(Plugin owner) {
        entryProviders.unregisterAll(owner);
        contentProviders.unregisterAll(owner);
        conditions.unregisterAll(owner);
        actions.unregisterAll(owner);
    }

    /**
     * Wraps Keystone's handle in HoloPanels' own.
     * <p>
     * Not a cosmetic conversion: {@code holopanels-api} is published, and
     * Keystone is relocated into this jar, so handing a caller a
     * {@code dev.bwmp.keystone.registry.Registration} would make them compile
     * against a class name that does not exist at runtime.
     */
    private static Registration adapt(dev.bwmp.keystone.registry.Registration delegate) {
        return new Registration() {
            @Override
            public void close() {
                delegate.close();
            }

            @Override
            public boolean active() {
                return delegate.isActive();
            }
        };
    }
}
