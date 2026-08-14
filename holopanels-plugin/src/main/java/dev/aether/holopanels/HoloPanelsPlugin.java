package dev.aether.holopanels;

import dev.aether.holopanels.api.HoloPanels;
import dev.aether.holopanels.command.HoloPanelsCommand;
import dev.aether.holopanels.config.ConfigException;
import dev.aether.holopanels.config.HoloConfigService;
import dev.aether.holopanels.metrics.HoloPanelsMetrics;
import dev.aether.holopanels.model.ConfigSnapshot;
import dev.aether.holopanels.render.LayoutService;
import dev.aether.holopanels.render.PacketPanelRenderer;
import dev.aether.holopanels.render.TextDisplayMetadataSchema;
import dev.aether.holopanels.runtime.ActionService;
import dev.aether.holopanels.runtime.ConditionService;
import dev.aether.holopanels.runtime.ContentService;
import dev.aether.holopanels.runtime.ExtensionRegistry;
import dev.aether.holopanels.runtime.HoloPanelsApiImpl;
import dev.aether.holopanels.runtime.InteractionService;
import dev.aether.holopanels.runtime.SessionManager;
import dev.aether.holopanels.runtime.VisibilityService;
import dev.aether.holopanels.text.TemplateService;
import dev.bwmp.keystone.Keystone;
import dev.bwmp.keystone.KeystoneHandle;
import dev.bwmp.keystone.config.ManagedConfig;
import dev.bwmp.keystone.config.Snapshot;
import dev.bwmp.keystone.scheduler.KeystoneScheduler;
import dev.bwmp.keystone.text.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class HoloPanelsPlugin extends JavaPlugin implements Listener {

    /**
     * The active configuration, replaced wholesale rather than mutated.
     * <p>
     * Was a bare {@code volatile} field doing the same job by hand. Keystone's
     * holder is the same idea named once: a reader sees the old configuration
     * or the new one, never a half-applied reload.
     */
    private Snapshot<ConfigSnapshot> snapshot;
    private KeystoneHandle keystone;
    private KeystoneScheduler scheduler;
    private HoloConfigService configService;
    private MessageService messages;
    private ExtensionRegistry extensions;
    private SessionManager sessions;
    private PacketPanelRenderer renderer;
    private VisibilityService visibility;
    private HoloPanelsApiImpl api;

    @Override
    public void onEnable() {
        keystone = Keystone.bootstrap(this);
        scheduler = keystone.scheduler();

        configService = new HoloConfigService(this);
        configService.createDefaults();
        messages = keystone.messages();

        try {
            snapshot = new Snapshot<>(configService.load());
        } catch (ConfigException exception) {
            getLogger().log(Level.SEVERE, "HoloPanels configuration is invalid; disabling.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Each registry is created through Keystone, which also installs the
        // PluginDisableEvent listener that drops a departing plugin's entries.
        // That listener used to live in this class and had to be remembered.
        extensions = new ExtensionRegistry(
                keystone.registry("entry provider"),
                keystone.registry("content provider"),
                keystone.registry("condition"),
                keystone.registry("action"));

        sessions = new SessionManager();
        TextDisplayMetadataSchema schema = TextDisplayMetadataSchema.forVersion(keystone.platform().version());
        renderer = new PacketPanelRenderer(schema);
        TemplateService templates = new TemplateService(getServer().getPluginManager());
        ConditionService conditions = new ConditionService(this, extensions);
        ContentService content = new ContentService(
                this, scheduler, extensions, templates,
                snapshot.get().settings().maxEntriesPerProvider(), this::refresh);
        LayoutService layout = new LayoutService(templates, conditions, content);
        visibility = new VisibilityService(this, scheduler, this::snapshot, sessions, conditions, layout, renderer);
        ActionService actions = new ActionService(
                this, scheduler, extensions, templates, messages, sessions, visibility, this::snapshot);
        InteractionService interactions = new InteractionService(this::snapshot, sessions, renderer, actions);
        api = new HoloPanelsApiImpl(this, scheduler, extensions, sessions, visibility, this::snapshot);

        extensions.onChanged(() -> {
            sessions.clear();
            visibility.refreshAll();
        });

        getServer().getServicesManager().register(HoloPanels.class, api, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(interactions, this);

        new HoloPanelsCommand(this, messages).build().bind(this, "holopanels");

        visibility.start();
        // Named at startup rather than left to be inferred from a bug report:
        // which server this thinks it is on, and which display layout it picked
        // from that, are the first two things a rendering problem turns on.
        keystone.platform().describe().forEach(getLogger()::info);
        if (schema.isLegacy()) {
            getLogger().info("Layout:   pre-1.20.2 text display metadata");
        }
        getLogger().info("HoloPanels enabled with " + snapshot.get().boards().size() + " board(s) and "
                + snapshot.get().views().size() + " view(s).");

        // Last, so every sampler it registers has something to read — and past
        // the invalid-configuration return above, which disables the plugin.
        // Shutdown is wired to the Keystone handle, so there is nothing to undo.
        HoloPanelsMetrics.start(this);
    }

    @Override
    public void onDisable() {
        if (visibility != null) {
            visibility.stop();
        }
        if (renderer != null) {
            renderer.hideAll(Bukkit.getOnlinePlayers());
        }
        if (sessions != null) {
            sessions.clear();
        }
        getServer().getServicesManager().unregisterAll(this);
        if (keystone != null) {
            keystone.shutdown();
        }
    }

    public boolean reloadHoloPanels() {
        try {
            ConfigSnapshot candidate = configService.load();
            renderer.hideAll(Bukkit.getOnlinePlayers());
            sessions.clear();
            // Published only once loading has succeeded, so a bad edit leaves
            // the running configuration untouched.
            snapshot.set(candidate);
            messages.reload();
            visibility.start();
            visibility.refreshAll();
            return true;
        } catch (ConfigException exception) {
            getLogger().log(Level.SEVERE, "HoloPanels reload rejected; keeping the active configuration.", exception);
            return false;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduler.atEntity(event.getPlayer(), () -> visibility.refresh(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        renderer.hideAll(event.getPlayer());
        sessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        renderer.hideAll(event.getPlayer());
        scheduler.atEntity(event.getPlayer(), () -> visibility.refresh(event.getPlayer()));
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        scheduler.runLater(visibility::refreshAll, 2L);
    }

    public ConfigSnapshot snapshot() {
        return snapshot.get();
    }

    public HoloConfigService configService() {
        return configService;
    }

    public PacketPanelRenderer renderer() {
        return renderer;
    }

    public VisibilityService visibility() {
        return visibility;
    }

    public HoloPanelsApiImpl api() {
        return api;
    }

    public KeystoneScheduler scheduler() {
        return scheduler;
    }

    public KeystoneHandle keystone() {
        return keystone;
    }

    public SessionManager sessions() {
        return sessions;
    }

    public ExtensionRegistry extensions() {
        return extensions;
    }

    public ManagedConfig managedConfig(String resource) {
        return keystone.config(resource);
    }

    private void refresh(org.bukkit.entity.Player player, org.bukkit.NamespacedKey boardId) {
        if (visibility != null) {
            visibility.refresh(player, boardId);
        }
    }
}
