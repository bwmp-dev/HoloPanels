package dev.aether.holopanels.command;

import dev.aether.holopanels.HoloPanelsPlugin;
import dev.aether.holopanels.config.ConfigException;
import dev.aether.holopanels.model.BoardDefinition;
import dev.aether.holopanels.model.ConfigSnapshot;
import dev.aether.holopanels.render.RenderedPanel;
import dev.bwmp.keystone.command.CommandContext;
import dev.bwmp.keystone.command.RootCommand;
import dev.bwmp.keystone.command.SimpleSubcommand;
import dev.bwmp.keystone.text.KeystoneText;
import dev.bwmp.keystone.text.MessageService;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The {@code /holopanels} command tree.
 * <p>
 * Rebuilt on Keystone's {@link RootCommand}. Permission and player-only checks
 * are declared per subcommand and enforced once by the tree, rather than
 * repeated as the first lines of each handler — which is where one omission
 * used to be an unguarded command.
 */
public final class HoloPanelsCommand {

    private final HoloPanelsPlugin plugin;
    private final MessageService messages;

    public HoloPanelsCommand(HoloPanelsPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public RootCommand build() {
        RootCommand root = new RootCommand(messages, "usage");

        root.register(SimpleSubcommand.of("reload", this::reload)
                .permission("holopanels.admin.reload")
                .description("Reload boards and views"));

        root.register(SimpleSubcommand.of("validate", this::validate)
                .permission("holopanels.admin.reload")
                .description("Check the configuration without applying it"));

        root.register(SimpleSubcommand.of("list", this::list)
                .description("List configured boards"));

        root.register(SimpleSubcommand.of("info", this::info)
                .usage("info <board>")
                .description("Show a board's configuration")
                .completer((sender, args) -> RootCommand.matching(boardIds(), args.get(0, ""))));

        root.register(SimpleSubcommand.of("here", this::here)
                .permission("holopanels.admin.move")
                .requiresPlayer()
                .usage("here <board>")
                .description("Move a board to your position")
                .completer((sender, args) -> RootCommand.matching(boardIds(), args.get(0, ""))));

        root.register(SimpleSubcommand.of("debug", this::debug)
                .permission("holopanels.admin.debug")
                .requiresPlayer()
                .description("List the panels currently rendered to you"));

        root.register(SimpleSubcommand.of("refresh", this::refresh)
                .permission("holopanels.admin.debug")
                .usage("refresh [board]")
                .description("Redraw one board or all of them")
                .completer((sender, args) -> RootCommand.matching(boardIds(), args.get(0, ""))));

        root.defaultTo(SimpleSubcommand.of("help", this::help).description("Show this help"));
        return root;
    }

    private void help(CommandContext context) {
        line(context.sender(), "<gray>--- <aqua>HoloPanels<gray> ---");
        for (dev.bwmp.keystone.command.Subcommand subcommand : build().subcommands()) {
            if (!subcommand.permission().isBlank() && !context.sender().hasPermission(subcommand.permission())) {
                continue;
            }
            line(context.sender(), "<yellow>/holopanels " + subcommand.usage()
                    + " <dark_gray>- <gray>" + subcommand.description());
        }
    }

    private void reload(CommandContext context) {
        if (plugin.reloadHoloPanels()) {
            ConfigSnapshot snapshot = plugin.snapshot();
            messages.send(context.sender(), "reload-complete",
                    MessageService.value("boards", String.valueOf(snapshot.boards().size())),
                    MessageService.value("views", String.valueOf(snapshot.views().size())));
        } else {
            messages.send(context.sender(), "reload-failed");
        }
    }

    private void validate(CommandContext context) {
        try {
            ConfigSnapshot candidate = plugin.configService().load();
            messages.send(context.sender(), "validate-complete",
                    MessageService.value("boards", String.valueOf(candidate.boards().size())),
                    MessageService.value("views", String.valueOf(candidate.views().size())));
        } catch (ConfigException exception) {
            plugin.getLogger().warning("Validation failed: " + exception.getMessage());
            messages.send(context.sender(), "reload-failed");
        }
    }

    private void list(CommandContext context) {
        messages.send(context.sender(), "list-header");
        for (BoardDefinition board : plugin.snapshot().boards().values()) {
            String state = board.location().flatMap(location -> location.resolve()).isPresent()
                    ? "active"
                    : "dormant";
            messages.send(context.sender(), "list-entry",
                    MessageService.value("board", board.id().toString()),
                    MessageService.value("view", board.rootView().toString()),
                    MessageService.value("state", state));
        }
    }

    private void info(CommandContext context) {
        String raw = context.args().get(0, "");
        BoardDefinition board = board(raw);
        if (board == null) {
            messages.send(context.sender(), "board-not-found", MessageService.value("board", raw));
            return;
        }

        // Escaped because a board id comes from config and could otherwise be
        // read as markup once it is spliced into a MiniMessage string.
        line(context.sender(), "<aqua>" + KeystoneText.escape(board.id().toString())
                + "</aqua> <gray>-></gray> <white>" + KeystoneText.escape(board.rootView().toString()) + "</white>");
        line(context.sender(), "<gray>Range:</gray> <white>" + board.visibilityDistance()
                + "</white> <gray>Click:</gray> <white>" + board.clickDistance() + "</white>");
        line(context.sender(), "<gray>Anchor:</gray> <white>"
                + KeystoneText.escape(board.location().map(Object::toString).orElse("not positioned")) + "</white>");
    }

    private void here(CommandContext context) {
        Player player = context.requirePlayer();
        String raw = context.args().get(0, "");
        BoardDefinition board = board(raw);
        if (board == null) {
            messages.send(context.sender(), "board-not-found", MessageService.value("board", raw));
            return;
        }

        try {
            plugin.configService().moveBoard(board.id(), player.getLocation());
            if (plugin.reloadHoloPanels()) {
                messages.send(context.sender(), "board-moved",
                        MessageService.value("board", board.id().toString()));
            } else {
                messages.send(context.sender(), "reload-failed");
            }
        } catch (ConfigException exception) {
            plugin.getLogger().warning("Could not move board " + board.id() + ": " + exception.getMessage());
            messages.send(context.sender(), "reload-failed");
        }
    }

    private void debug(CommandContext context) {
        Player player = context.requirePlayer();
        List<RenderedPanel> panels = plugin.renderer().panels(player);
        line(context.sender(), "<gray>Visible panels:</gray> <white>" + panels.size() + "</white>");
        for (RenderedPanel panel : panels) {
            line(context.sender(), "<dark_gray>-</dark_gray> <white>"
                    + KeystoneText.escape(panel.renderId()) + "</white> <gray>lines "
                    + panel.lineCount() + ", regions " + panel.clickRegions().size() + "</gray>");
        }
    }

    private void refresh(CommandContext context) {
        String raw = context.args().get(0, "");
        if (raw.isEmpty()) {
            plugin.visibility().refreshAll();
            return;
        }

        BoardDefinition board = board(raw);
        if (board == null) {
            messages.send(context.sender(), "board-not-found", MessageService.value("board", raw));
            return;
        }
        plugin.api().refresh(board.id());
    }

    private BoardDefinition board(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        NamespacedKey id = NamespacedKey.fromString(raw.toLowerCase(Locale.ROOT), plugin);
        return id == null ? null : plugin.snapshot().boards().get(id);
    }

    private List<String> boardIds() {
        List<String> ids = new ArrayList<>();
        plugin.snapshot().boards().keySet().forEach(key -> ids.add(key.toString()));
        return ids;
    }

    private void line(CommandSender sender, String miniMessage) {
        messages.sendComponent(sender, KeystoneText.parse(miniMessage));
    }
}
