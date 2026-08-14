package dev.aether.holopanels.metrics;

import dev.aether.holopanels.HoloPanelsPlugin;
import dev.aether.holopanels.model.ConfigSnapshot;
import dev.aether.holopanels.model.ViewDefinition;
import dev.aether.holopanels.render.TextDisplayMetadataSchema;
import dev.bwmp.keystone.metrics.Chart;
import dev.bwmp.keystone.metrics.KeystoneMetrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What HoloPanels reports, and where to.
 * <p>
 * Every sampler reads either the published {@link ConfigSnapshot} — immutable
 * and swapped in one write — or a ConcurrentHashMap's size. None of them walk a
 * collection a reload mutates: bStats samples on its own thread on Folia, so a
 * sampler iterating live state would be a ConcurrentModificationException that
 * appears on exactly one platform and nowhere a developer would look for it.
 */
public final class HoloPanelsMetrics {

    private static final int BSTATS_SERVICE_ID = 33368;
    private static final String TELEMETRY_URL = "https://plugins.metrics.bwmp.dev";
    private static final String TELEMETRY_PROJECT = "holopanels";

    private HoloPanelsMetrics() {
    }

    public static void start(HoloPanelsPlugin plugin) {
        KeystoneMetrics.builder(plugin.keystone())
                .bstats(BSTATS_SERVICE_ID)
                .telemetry(TELEMETRY_URL, TELEMETRY_PROJECT)
                .chart(Chart.singleLine("boards", () -> plugin.snapshot().boards().size()))
                .chart(Chart.singleLine("views", () -> plugin.snapshot().views().size()))
                .chart(Chart.singleLine("panels", () -> panels(plugin)))
                .chart(Chart.singleLine("sessions", () -> plugin.sessions().size()))
                .chart(Chart.singleLine("extensions", () -> plugin.extensions().size()))
                .chart(Chart.advancedPie("addons", () -> addons(plugin)))
                .chart(Chart.simplePie("display_metadata", () -> displayMetadata(plugin)))
                .chart(Chart.simplePie("placeholderapi",
                        () -> Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") ? "Present" : "Absent"))
                .start();
    }

    private static int panels(HoloPanelsPlugin plugin) {
        int total = 0;
        for (ViewDefinition view : plugin.snapshot().views().values()) {
            total += view.panels().size();
        }
        return total;
    }

    /**
     * Which of the two text display metadata layouts this server needs.
     * <p>
     * The legacy branch exists only for pre-1.20.2, and the count of servers
     * still on it is what decides when that branch can go.
     */
    private static String displayMetadata(HoloPanelsPlugin plugin) {
        return TextDisplayMetadataSchema.forVersion(plugin.keystone().platform().version()).isLegacy()
                ? "Legacy (pre-1.20.2)"
                : "Modern";
    }

    /** The plugins built on HoloPanels, which is what decides whether the API is worth keeping. */
    private static Map<String, Integer> addons(HoloPanelsPlugin plugin) {
        String name = plugin.getName();
        Map<String, Integer> found = new LinkedHashMap<>();
        for (Plugin other : Bukkit.getPluginManager().getPlugins()) {
            if (other == plugin) {
                continue;
            }
            if (other.getDescription().getDepend().contains(name)
                    || other.getDescription().getSoftDepend().contains(name)) {
                found.put(other.getName(), 1);
            }
        }
        return found;
    }
}
