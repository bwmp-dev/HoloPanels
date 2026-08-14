package dev.bwmp.holopanels.api;

import java.util.List;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface EntryProvider {
    CompletionStage<List<PanelEntry>> entries(EntryRequest request);
}
