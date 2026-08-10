package dev.aether.holopanels.api;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface ActionHandler {
    CompletionStage<ActionResult> execute(ActionContext context);
}
