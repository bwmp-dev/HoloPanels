package dev.aether.holopanels.api;

import net.kyori.adventure.text.Component;

import java.util.Objects;
import java.util.Optional;

public record ActionResult(ActionStatus status, Optional<Component> message, boolean refresh) {
    public ActionResult {
        Objects.requireNonNull(status, "status");
        message = Objects.requireNonNull(message, "message");
    }

    public static ActionResult success() {
        return new ActionResult(ActionStatus.SUCCESS, Optional.empty(), true);
    }

    public static ActionResult success(Component message) {
        return new ActionResult(ActionStatus.SUCCESS, Optional.of(message), true);
    }

    public static ActionResult denied(Component message) {
        return new ActionResult(ActionStatus.DENIED, Optional.of(message), false);
    }

    public static ActionResult failure(Component message) {
        return new ActionResult(ActionStatus.FAILURE, Optional.of(message), false);
    }
}
