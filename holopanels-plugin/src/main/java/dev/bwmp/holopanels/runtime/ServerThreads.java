package dev.bwmp.holopanels.runtime;

import dev.bwmp.keystone.scheduler.KeystoneScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Where work has to run before it is allowed to read a player or the world
 * around them.
 * <p>
 * Off Folia there is one tick thread and it owns every region, so work already
 * on it runs inline — handing it to the scheduler would only delay it to the
 * next tick. On Folia there is no such thread: a player belongs to whichever
 * region thread is ticking them, and that thread has to be asked for by name.
 * <p>
 * Written once rather than at each call site because the predicate is short
 * enough to look obvious and wrong in three slightly different ways.
 */
final class ServerThreads {

    private ServerThreads() {
    }

    /** Runs on the thread that owns {@code player}. */
    static void atPlayer(KeystoneScheduler scheduler, Player player, Runnable task) {
        if (ownsEverything(scheduler)) {
            task.run();
        } else {
            scheduler.atEntity(player, task);
        }
    }

    /** Runs where work belonging to no particular region belongs. */
    static void global(KeystoneScheduler scheduler, Runnable task) {
        if (ownsEverything(scheduler)) {
            task.run();
        } else {
            scheduler.run(task);
        }
    }

    private static boolean ownsEverything(KeystoneScheduler scheduler) {
        return !scheduler.isFolia() && Bukkit.isPrimaryThread();
    }
}
