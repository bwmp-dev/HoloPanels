package dev.bwmp.holopanels.runtime;

import dev.bwmp.holopanels.api.ActionContext;
import dev.bwmp.holopanels.api.ActionHandler;
import dev.bwmp.holopanels.api.ActionResult;
import dev.bwmp.holopanels.api.ActionStatus;
import dev.bwmp.holopanels.api.ClickType;
import dev.bwmp.holopanels.api.PanelEntry;
import dev.bwmp.holopanels.model.ActionDefinition;
import dev.bwmp.holopanels.model.BoardDefinition;
import dev.bwmp.holopanels.model.ViewDefinition;
import dev.bwmp.holopanels.render.ClickRegion;
import dev.bwmp.keystone.text.MessageService;
import dev.bwmp.holopanels.text.TemplateService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class ActionService {
    private final JavaPlugin plugin;
    private final ExtensionRegistry extensions;
    private final TemplateService templates;
    private final MessageService messages;
    private final SessionManager sessions;
    private final VisibilityService visibility;
    private final java.util.function.Supplier<dev.bwmp.holopanels.model.ConfigSnapshot> snapshot;
    private final dev.bwmp.keystone.scheduler.KeystoneScheduler scheduler;

    public ActionService(
            JavaPlugin plugin,
            dev.bwmp.keystone.scheduler.KeystoneScheduler scheduler,
            ExtensionRegistry extensions,
            TemplateService templates,
            MessageService messages,
            SessionManager sessions,
            VisibilityService visibility,
            java.util.function.Supplier<dev.bwmp.holopanels.model.ConfigSnapshot> snapshot
    ) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.extensions = extensions;
        this.templates = templates;
        this.messages = messages;
        this.sessions = sessions;
        this.visibility = visibility;
        this.snapshot = snapshot;
    }

    public void execute(
            Player player,
            BoardDefinition board,
            ViewDefinition view,
            String panelId,
            ClickRegion region,
            ClickType clickType,
            ViewerSession session
    ) {
        List<ActionDefinition> actions = region.actions().getOrDefault(clickType, List.of());
        if (actions.isEmpty()) {
            return;
        }

        CompletionStage<ActionResult> chain = CompletableFuture.completedFuture(ActionResult.success());
        for (ActionDefinition action : actions) {
            chain = chain.thenCompose(previous -> {
                if (previous.status() != ActionStatus.SUCCESS) {
                    return CompletableFuture.completedFuture(previous);
                }
                return executeOne(player, board, view, panelId, region.entry(), clickType, session, action)
                        .thenApply(result -> result.status() != ActionStatus.SUCCESS && action.continueOnFailure()
                                ? new ActionResult(ActionStatus.SUCCESS, result.message(), true)
                                : result);
            });
        }
        chain.whenComplete((result, error) -> ServerThreads.atPlayer(scheduler, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Action chain failed at " + board.id() + "/" + view.id()
                        + "/" + panelId, error);
                messages.send(player, "action-failed");
                return;
            }
            result.message().ifPresent(player::sendMessage);
            if (result.refresh()) {
                visibility.refresh(player, board.id());
            }
        }));
    }

    private CompletionStage<ActionResult> executeOne(
            Player player,
            BoardDefinition board,
            ViewDefinition view,
            String panelId,
            Optional<PanelEntry> entry,
            ClickType clickType,
            ViewerSession session,
            ActionDefinition action
    ) {
        String permission = action.arguments().getOrDefault("permission", "");
        if (!permission.isBlank() && !player.hasPermission(permission)) {
            return CompletableFuture.completedFuture(ActionResult.denied(
                    templates.render(action.arguments().getOrDefault("denied-message", "<red>You cannot use that."),
                            player, board.id(), view.id(), entry, session.state())));
        }

        String actionKey = board.id() + "/" + view.id() + "/" + panelId + "/" + action.type()
                + "/" + entry.map(PanelEntry::id).orElse("none");
        long now = System.currentTimeMillis();
        if (session.onCooldown(actionKey, now)) {
            return CompletableFuture.completedFuture(ActionResult.denied(
                    templates.render(action.arguments().getOrDefault("cooldown-message", "<red>Please wait before using that again."),
                            player, board.id(), view.id(), entry, session.state())));
        }

        String confirmation = action.arguments().getOrDefault("confirm-message", "");
        if (!confirmation.isBlank()) {
            String confirmKey = "_confirm." + Integer.toUnsignedString(actionKey.hashCode(), 36);
            long expires = parseLong(session.state().get(confirmKey), 0L);
            if (expires < now) {
                long seconds = Math.max(1L, parseLong(action.arguments().get("confirm-seconds"), 5L));
                session.state(confirmKey, String.valueOf(now + seconds * 1000L));
                return CompletableFuture.completedFuture(ActionResult.denied(
                        templates.render(confirmation, player, board.id(), view.id(), entry, session.state())));
            }
            session.state(confirmKey, "0");
        }

        CompletionStage<ActionResult> result;
        try {
            result = builtIn(player, board, view, panelId, entry, clickType, session, action);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }

        long cooldownTicks = Math.max(0L, parseLong(action.arguments().get("cooldown-ticks"), 0L));
        if (cooldownTicks > 0) {
            result = result.thenApply(value -> {
                if (value.status() == ActionStatus.SUCCESS) {
                    session.cooldown(actionKey, System.currentTimeMillis() + cooldownTicks * 50L);
                }
                return value;
            });
        }
        return result;
    }

    private CompletionStage<ActionResult> builtIn(
            Player player,
            BoardDefinition board,
            ViewDefinition view,
            String panelId,
            Optional<PanelEntry> entry,
            ClickType clickType,
            ViewerSession session,
            ActionDefinition action
    ) {
        Map<String, String> args = action.arguments();
        return switch (action.type()) {
            case "select" -> {
                if (entry.isEmpty()) {
                    yield denied(player, board, view, entry, session, "<red>There is nothing to select.");
                }
                session.select(args.getOrDefault("panel", panelId), entry.get().id());
                yield success();
            }
            case "clear-selection" -> {
                session.clearSelection(args.getOrDefault("panel", panelId));
                yield success();
            }
            case "open-view" -> {
                NamespacedKey target = NamespacedKey.fromString(args.getOrDefault("view", ""), plugin);
                if (target == null || !snapshot.get().views().containsKey(target)) {
                    yield denied(player, board, view, entry, session, "<red>That view is unavailable.");
                }
                session.currentView(target);
                yield success();
            }
            case "next-page" -> {
                String target = args.getOrDefault("panel", panelId);
                session.page(target, session.page(target) + 1);
                yield success();
            }
            case "previous-page" -> {
                String target = args.getOrDefault("panel", panelId);
                session.page(target, Math.max(0, session.page(target) - 1));
                yield success();
            }
            case "set-page" -> {
                String target = args.getOrDefault("panel", panelId);
                session.page(target, Math.max(0, (int) parseLong(args.get("page"), 1L) - 1));
                yield success();
            }
            case "command" -> command(player, board, view, entry, session, args);
            case "message" -> {
                player.sendMessage(render(args.getOrDefault("message", ""), player, board, view, entry, session));
                yield success();
            }
            case "action-bar" -> {
                player.sendActionBar(render(args.getOrDefault("message", ""), player, board, view, entry, session));
                yield success();
            }
            case "title" -> {
                Component title = render(args.getOrDefault("title", ""), player, board, view, entry, session);
                Component subtitle = render(args.getOrDefault("subtitle", ""), player, board, view, entry, session);
                player.showTitle(Title.title(title, subtitle, Title.Times.times(
                        Duration.ofMillis(parseLong(args.get("fade-in-ms"), 250L)),
                        Duration.ofMillis(parseLong(args.get("stay-ms"), 2000L)),
                        Duration.ofMillis(parseLong(args.get("fade-out-ms"), 250L)))));
                yield success();
            }
            case "link" -> {
                String url = args.getOrDefault("url", "");
                if (!url.startsWith("https://") && !url.startsWith("http://")) {
                    yield denied(player, board, view, entry, session, "<red>That link is invalid.");
                }
                Component label = render(args.getOrDefault("message", "<aqua><underlined>Open link</underlined></aqua>"),
                        player, board, view, entry, session).clickEvent(ClickEvent.openUrl(url));
                player.sendMessage(label);
                yield success();
            }
            // The two that read the player or hand them to somebody else's
            // code. Every other case above touches only session state and
            // outgoing packets, which are safe from any thread, so anchoring
            // them too would only cost the chain a tick each on Folia.
            case "teleport" -> atPlayer(player, () -> teleport(player, board, view, entry, session, args));
            case "set-state" -> {
                session.state(args.getOrDefault("key", ""), args.getOrDefault("value", "true"));
                yield success();
            }
            case "toggle-state" -> {
                String key = args.getOrDefault("key", "");
                boolean value = Boolean.parseBoolean(session.state().getOrDefault(key, "false"));
                session.state(key, String.valueOf(!value));
                yield success();
            }
            case "refresh" -> success();
            case "close" -> {
                session.hidden(true);
                visibility.hide(player, board.id());
                yield success();
            }
            case "reset" -> {
                session.reset(board.rootView());
                yield success();
            }
            case "custom" -> atPlayer(player, () ->
                    custom(player, board, view, panelId, entry, clickType, session, args));
            default -> denied(player, board, view, entry, session, "<red>Unknown action: " + action.type());
        };
    }

    /**
     * Starts {@code work} on the thread that owns the player and reports its
     * result back into the chain.
     * <p>
     * Only needed once an earlier action in the same chain has completed
     * asynchronously, which leaves the rest of the chain running on whichever
     * thread that action finished on. The synchronous throw is caught here
     * because {@code executeOne}'s guard sits on the calling thread and would
     * no longer see it.
     */
    private CompletionStage<ActionResult> atPlayer(Player player, Supplier<CompletionStage<ActionResult>> work) {
        CompletableFuture<ActionResult> result = new CompletableFuture<>();
        ServerThreads.atPlayer(scheduler, player, () -> {
            try {
                work.get().whenComplete((value, error) -> {
                    if (error != null) {
                        result.completeExceptionally(error);
                    } else {
                        result.complete(value);
                    }
                });
            } catch (RuntimeException exception) {
                result.completeExceptionally(exception);
            }
        });
        return result;
    }

    private CompletionStage<ActionResult> command(
            Player player,
            BoardDefinition board,
            ViewDefinition view,
            Optional<PanelEntry> entry,
            ViewerSession session,
            Map<String, String> args
    ) {
        String command = args.getOrDefault("command", "");
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        command = command
                .replace("<player>", player.getName())
                .replace("<uuid>", player.getUniqueId().toString())
                .replace("<entry_id>", entry.map(PanelEntry::id).orElse(""));
        if (command.isBlank()) {
            return denied(player, board, view, entry, session, "<red>No command was configured.");
        }
        boolean console = args.getOrDefault("executor", "player").equalsIgnoreCase("console");
        String dispatched = command;
        CompletableFuture<ActionResult> result = new CompletableFuture<>();
        // A console command belongs to no region and may touch any of them, so
        // on Folia it goes to the global thread; a player's own command goes to
        // the thread ticking that player. Off Folia both are the caller's.
        Runnable dispatch = () -> result.complete(
                Bukkit.dispatchCommand(console ? Bukkit.getConsoleSender() : player, dispatched)
                        ? ActionResult.success()
                        : ActionResult.failure(Component.text("The command was not accepted.")));
        if (console) {
            ServerThreads.global(scheduler, dispatch);
        } else {
            ServerThreads.atPlayer(scheduler, player, dispatch);
        }
        return result;
    }

    private CompletionStage<ActionResult> teleport(
            Player player,
            BoardDefinition board,
            ViewDefinition view,
            Optional<PanelEntry> entry,
            ViewerSession session,
            Map<String, String> args
    ) {
        World world = Bukkit.getWorld(args.getOrDefault("world", player.getWorld().getName()));
        if (world == null) {
            return denied(player, board, view, entry, session, "<red>That world is unavailable.");
        }
        try {
            Location current = player.getLocation();
            Location location = new Location(
                    world,
                    Double.parseDouble(args.getOrDefault("x", String.valueOf(current.getX()))),
                    Double.parseDouble(args.getOrDefault("y", String.valueOf(current.getY()))),
                    Double.parseDouble(args.getOrDefault("z", String.valueOf(current.getZ()))),
                    Float.parseFloat(args.getOrDefault("yaw", String.valueOf(current.getYaw()))),
                    Float.parseFloat(args.getOrDefault("pitch", String.valueOf(current.getPitch())))
            );
            // Folia has no synchronous teleport at all, and off Folia this
            // still avoids teleporting inside event handling.
            return scheduler.teleport(player, location).thenApply(moved -> moved
                    ? ActionResult.success()
                    : ActionResult.failure(Component.text("The teleport was refused.")));
        } catch (NumberFormatException exception) {
            return denied(player, board, view, entry, session, "<red>The teleport location is invalid.");
        }
    }

    private CompletionStage<ActionResult> custom(
            Player player,
            BoardDefinition board,
            ViewDefinition view,
            String panelId,
            Optional<PanelEntry> entry,
            ClickType clickType,
            ViewerSession session,
            Map<String, String> args
    ) {
        NamespacedKey id = NamespacedKey.fromString(args.getOrDefault("id", ""), plugin);
        Optional<ActionHandler> handler = id == null ? Optional.empty() : extensions.action(id);
        if (handler.isEmpty()) {
            return denied(player, board, view, entry, session, "<red>That action is unavailable.");
        }
        Map<String, String> arguments = args.entrySet().stream()
                .filter(value -> !value.getKey().equals("id"))
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        return handler.get().execute(new ActionContext(
                player, board.id(), view.id(), panelId, clickType, entry, arguments, session.state()));
    }

    private CompletionStage<ActionResult> denied(
            Player player,
            BoardDefinition board,
            ViewDefinition view,
            Optional<PanelEntry> entry,
            ViewerSession session,
            String text
    ) {
        return CompletableFuture.completedFuture(ActionResult.denied(render(text, player, board, view, entry, session)));
    }

    private CompletionStage<ActionResult> success() {
        return CompletableFuture.completedFuture(ActionResult.success());
    }

    private Component render(
            String text,
            Player player,
            BoardDefinition board,
            ViewDefinition view,
            Optional<PanelEntry> entry,
            ViewerSession session
    ) {
        return templates.render(text, player, board.id(), view.id(), entry, session.state());
    }

    private long parseLong(String value, long fallback) {
        try {
            return value == null ? fallback : Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
