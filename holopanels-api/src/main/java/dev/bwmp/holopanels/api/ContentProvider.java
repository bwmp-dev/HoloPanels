package dev.bwmp.holopanels.api;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface ContentProvider {
    CompletionStage<List<Component>> content(ContentRequest request);
}
