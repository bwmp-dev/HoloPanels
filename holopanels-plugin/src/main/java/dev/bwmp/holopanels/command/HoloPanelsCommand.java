package dev.bwmp.holopanels.command;

import dev.bwmp.holopanels.HoloPanelsPlugin;
import dev.bwmp.holopanels.config.ConfigException;
import dev.bwmp.holopanels.model.BoardDefinition;
import dev.bwmp.holopanels.model.BoardLocation;
import dev.bwmp.holopanels.model.ConfigSnapshot;
import dev.bwmp.holopanels.render.PanelGeometry;
import dev.bwmp.holopanels.render.RenderedPanel;
import dev.bwmp.keystone.command.CommandArguments;
import dev.bwmp.keystone.command.CommandContext;
import dev.bwmp.keystone.command.RootCommand;
import dev.bwmp.keystone.command.SimpleSubcommand;
import dev.bwmp.keystone.text.KeystoneText;
import dev.bwmp.keystone.text.MessageService;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The {@code /holopanels} command tree.
 * <p>
 * Rebuilt on Keystone's {@link RootCommand}. Permission and player-only checks
 * are declared per subcommand and enforced once by the tree, rather than
 * repeated as the first lines of each handler — which is where one omission
 * used to be an unguarded command.
 */
public final class HoloPanelsCommand {

    /** How far {@code wall} will look for a block to place against. */
    private static final double WALL_REACH = 12.0;

    private static final double DEFAULT_WALL_GAP = 0.05;

    private static final double DEFAULT_NUDGE = 0.1;

    private static final List<String> DIRECTIONS =
            List.of("right", "left", "up", "down", "forward", "back");

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

        root.register(SimpleSubcommand.of("wall", this::wall)
                .permission("holopanels.admin.move")
                .requiresPlayer()
                .usage("wall <board> [gap]")
                .description("Place a board flat against the block you are looking at")
                .completer((sender, args) -> args.size() <= 1
                        ? RootCommand.matching(boardIds(), args.get(0, ""))
                        : List.of()));

        root.register(SimpleSubcommand.of("move", this::move)
                .permission("holopanels.admin.move")
                .usage("move <board> <x> <y> <z> [yaw]")
                .description("Move a board to exact coordinates; ~ keeps a value")
                .completer((sender, args) -> switch (args.size()) {
                    case 0, 1 -> RootCommand.matching(boardIds(), args.get(0, ""));
                    case 2, 3, 4, 5 -> RootCommand.matching(List.of("~"), args.get(args.size() - 1, ""));
                    default -> List.of();
                }));

        root.register(SimpleSubcommand.of("nudge", this::nudge)
                .permission("holopanels.admin.move")
                .usage("nudge <board> <" + String.join("|", DIRECTIONS) + "> [distance]")
                .description("Shift a placed board along its own axes")
                .completer((sender, args) -> switch (args.size()) {
                    case 0, 1 -> RootCommand.matching(boardIds(), args.get(0, ""));
                    case 2 -> RootCommand.matching(DIRECTIONS, args.get(1, ""));
                    default -> List.of();
                }));

        root.register(SimpleSubcommand.of("unplace", this::unplace)
                .permission("holopanels.admin.move")
                .usage("unplace <board>")
                .description("Clear a board's anchor without deleting it")
                .completer((sender, args) -> args.size() <= 1
                        ? RootCommand.matching(boardIds(), args.get(0, ""))
                        : List.of()));

        root.register(SimpleSubcommand.of("remove", this::remove)
                .aliases("delete")
                .permission("holopanels.admin.remove")
                .usage("remove <board> confirm")
                .description("Delete a board from boards.yml")
                .completer((sender, args) -> switch (args.size()) {
                    case 0, 1 -> RootCommand.matching(boardIds(), args.get(0, ""));
                    case 2 -> RootCommand.matching(List.of("confirm"), args.get(1, ""));
                    default -> List.of();
                }));

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
        BoardDefinition board = board(context);
        if (board == null) {
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
        BoardDefinition board = board(context);
        if (board == null) {
            return;
        }
        if (anchor(context, board, player.getLocation())) {
            messages.send(context.sender(), "board-moved",
                    MessageService.value("board", board.id().toString()));
        }
    }

    private void wall(CommandContext context) {
        Player player = context.requirePlayer();
        BoardDefinition board = board(context);
        if (board == null) {
            return;
        }

        double gap = DEFAULT_WALL_GAP;
        if (context.args().optional(1).isPresent()) {
            OptionalDouble parsed = number(context.args().get(1, ""));
            if (parsed.isEmpty() || parsed.getAsDouble() < 0.0 || parsed.getAsDouble() > 5.0) {
                messages.send(context.sender(), "invalid-number",
                        MessageService.value("value", context.args().get(1, "")));
                return;
            }
            gap = parsed.getAsDouble();
        }

        RayTraceResult trace = player.rayTraceBlocks(WALL_REACH, FluidCollisionMode.NEVER);
        BlockFace face = trace == null ? null : trace.getHitBlockFace();
        if (trace == null || trace.getHitBlock() == null || face == null) {
            messages.send(context.sender(), "no-block-in-sight",
                    MessageService.value("distance", decimal(WALL_REACH)));
            return;
        }

        Vector normal = new Vector(face.getModX(), face.getModY(), face.getModZ());
        Location location = trace.getHitPosition()
                .add(normal.clone().multiply(gap))
                .toLocation(player.getWorld());
        // A board's normal is horizontal, so a floor or a ceiling gives it no
        // facing of its own. Turn it toward whoever placed it instead.
        location.setYaw(PanelGeometry.yawFacing(normal).orElseGet(() ->
                PanelGeometry.yawFacing(player.getEyeLocation().toVector().subtract(location.toVector()))
                        .orElse(PanelGeometry.normalizeYaw(player.getLocation().getYaw() + 180.0F))));
        location.setPitch(0.0F);

        if (anchor(context, board, location)) {
            messages.send(context.sender(), "board-placed",
                    MessageService.value("board", board.id().toString()),
                    MessageService.value("face", face.name().toLowerCase(Locale.ROOT)),
                    MessageService.value("position", position(location)));
        }
    }

    private void move(CommandContext context) {
        CommandArguments args = context.args();
        BoardDefinition board = board(context);
        if (board == null) {
            return;
        }
        if (args.size() < 4) {
            line(context.sender(), "<yellow>/holopanels move <board> <x> <y> <z> [yaw]"
                    + " <dark_gray>- <gray>~ keeps the board's current value");
            return;
        }

        // Coordinates are relative to where the board already is, so `~ ~1 ~`
        // raises it and `~ ~ ~ 90` turns it in place. An unplaced board has
        // nothing to be relative to, so it falls back to the sender.
        Location origin = board.location().flatMap(BoardLocation::resolve)
                .or(() -> context.player().map(Player::getLocation))
                .orElse(null);
        if (origin == null) {
            messages.send(context.sender(), "board-not-positioned",
                    MessageService.value("board", board.id().toString()));
            return;
        }

        OptionalDouble x = coordinate(args.get(1, ""), origin.getX());
        OptionalDouble y = coordinate(args.get(2, ""), origin.getY());
        OptionalDouble z = coordinate(args.get(3, ""), origin.getZ());
        OptionalDouble yaw = coordinate(args.get(4, "~"), origin.getYaw());
        if (x.isEmpty() || y.isEmpty() || z.isEmpty() || yaw.isEmpty()) {
            int bad = x.isEmpty() ? 1 : y.isEmpty() ? 2 : z.isEmpty() ? 3 : 4;
            messages.send(context.sender(), "invalid-number",
                    MessageService.value("value", args.get(bad, "")));
            return;
        }

        Location location = new Location(origin.getWorld(), x.getAsDouble(), y.getAsDouble(), z.getAsDouble(),
                PanelGeometry.normalizeYaw((float) yaw.getAsDouble()), 0.0F);
        if (anchor(context, board, location)) {
            messages.send(context.sender(), "board-repositioned",
                    MessageService.value("board", board.id().toString()),
                    MessageService.value("position", position(location)));
        }
    }

    private void nudge(CommandContext context) {
        CommandArguments args = context.args();
        BoardDefinition board = board(context);
        if (board == null) {
            return;
        }

        Optional<Location> current = board.location().flatMap(BoardLocation::resolve);
        if (current.isEmpty()) {
            messages.send(context.sender(), "board-not-positioned",
                    MessageService.value("board", board.id().toString()));
            return;
        }

        String direction = args.lower(1);
        if (!DIRECTIONS.contains(direction)) {
            messages.send(context.sender(), "invalid-direction",
                    MessageService.value("direction", args.get(1, "")),
                    MessageService.value("directions", String.join(", ", DIRECTIONS)));
            return;
        }

        double distance = DEFAULT_NUDGE;
        if (args.optional(2).isPresent()) {
            OptionalDouble parsed = number(args.get(2, ""));
            if (parsed.isEmpty()) {
                messages.send(context.sender(), "invalid-number",
                        MessageService.value("value", args.get(2, "")));
                return;
            }
            distance = parsed.getAsDouble();
        }

        // Board-relative, matching the axes a view's panel offsets use, so a
        // nudge reads the same way as the offsets an admin already wrote.
        Location moved = switch (direction) {
            case "right" -> PanelGeometry.offset(current.get(), distance, 0.0, 0.0);
            case "left" -> PanelGeometry.offset(current.get(), -distance, 0.0, 0.0);
            case "up" -> PanelGeometry.offset(current.get(), 0.0, distance, 0.0);
            case "down" -> PanelGeometry.offset(current.get(), 0.0, -distance, 0.0);
            case "forward" -> PanelGeometry.offset(current.get(), 0.0, 0.0, distance);
            default -> PanelGeometry.offset(current.get(), 0.0, 0.0, -distance);
        };

        if (anchor(context, board, moved)) {
            messages.send(context.sender(), "board-nudged",
                    MessageService.value("board", board.id().toString()),
                    MessageService.value("direction", direction),
                    MessageService.value("distance", decimal(distance)),
                    MessageService.value("position", position(moved)));
        }
    }

    private void unplace(CommandContext context) {
        BoardDefinition board = board(context);
        if (board == null) {
            return;
        }
        if (board.location().isEmpty()) {
            messages.send(context.sender(), "board-not-positioned",
                    MessageService.value("board", board.id().toString()));
            return;
        }

        if (edit(context, board, () -> plugin.configService().unplaceBoard(board.id()))) {
            messages.send(context.sender(), "board-unplaced",
                    MessageService.value("board", board.id().toString()));
        }
    }

    private void remove(CommandContext context) {
        BoardDefinition board = board(context);
        if (board == null) {
            return;
        }
        // Deleting a board takes its view binding, distances and conditions
        // with it, and boards.yml is not versioned. Ask once.
        if (!context.args().lower(1).equals("confirm")) {
            messages.send(context.sender(), "board-remove-confirm",
                    MessageService.value("board", board.id().toString()),
                    MessageService.value("view", board.rootView().toString()));
            return;
        }

        if (edit(context, board, () -> plugin.configService().removeBoard(board.id()))) {
            messages.send(context.sender(), "board-removed",
                    MessageService.value("board", board.id().toString()));
        }
    }

    private void debug(CommandContext context) {
        Player player = context.requirePlayer();
        List<RenderedPanel> panels = plugin.renderer().panels(player);
        line(context.sender(), "<gray>Visible panels:</gray> <white>" + panels.size() + "</white>");
        for (RenderedPanel panel : panels) {
            line(context.sender(), "<dark_gray>-</dark_gray> <white>"
                    + KeystoneText.escape(panel.renderId()) + "</white> <gray>lines "
                    + panel.bands().size() + ", regions " + panel.clickRegions().size() + "</gray>");
        }
    }

    private void refresh(CommandContext context) {
        String raw = context.args().get(0, "");
        if (raw.isEmpty()) {
            plugin.visibility().refreshAll();
            return;
        }

        BoardDefinition board = board(context);
        if (board == null) {
            return;
        }
        plugin.api().refresh(board.id());
    }

    /**
     * The board named by the first argument, or {@code null} after telling the
     * sender there is no such board. Every subcommand that takes a board id
     * starts here, so the lookup and its failure message are written once.
     */
    private BoardDefinition board(CommandContext context) {
        String raw = context.args().get(0, "");
        NamespacedKey id = raw.isBlank() ? null : NamespacedKey.fromString(raw.toLowerCase(Locale.ROOT), plugin);
        BoardDefinition board = id == null ? null : plugin.snapshot().boards().get(id);
        if (board == null) {
            messages.send(context.sender(), "board-not-found", MessageService.value("board", raw));
        }
        return board;
    }

    /** Writes a board's anchor and reloads. Every placement subcommand ends here. */
    private boolean anchor(CommandContext context, BoardDefinition board, Location location) {
        return edit(context, board, () -> plugin.configService().moveBoard(board.id(), location));
    }

    /**
     * Applies an edit to boards.yml and reloads, reporting either failure to
     * the sender. Returns whether the caller should announce success.
     */
    private boolean edit(CommandContext context, BoardDefinition board, ConfigEdit change) {
        try {
            change.apply();
        } catch (ConfigException exception) {
            plugin.getLogger().warning("Could not edit board " + board.id() + ": " + exception.getMessage());
            messages.send(context.sender(), "reload-failed");
            return false;
        }
        if (!plugin.reloadHoloPanels()) {
            messages.send(context.sender(), "reload-failed");
            return false;
        }
        return true;
    }

    private OptionalDouble coordinate(String raw, double origin) {
        if (!raw.startsWith("~")) {
            return number(raw);
        }
        String offset = raw.substring(1);
        if (offset.isEmpty()) {
            return OptionalDouble.of(origin);
        }
        OptionalDouble parsed = number(offset);
        return parsed.isPresent() ? OptionalDouble.of(origin + parsed.getAsDouble()) : parsed;
    }

    private OptionalDouble number(String raw) {
        try {
            double value = Double.parseDouble(raw);
            return Double.isFinite(value) ? OptionalDouble.of(value) : OptionalDouble.empty();
        } catch (NumberFormatException ignored) {
            return OptionalDouble.empty();
        }
    }

    private String decimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String position(Location location) {
        return decimal(location.getX()) + ", " + decimal(location.getY()) + ", " + decimal(location.getZ())
                + " (yaw " + decimal(location.getYaw()) + ")";
    }

    @FunctionalInterface
    private interface ConfigEdit {
        void apply() throws ConfigException;
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
