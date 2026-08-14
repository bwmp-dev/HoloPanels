package dev.bwmp.holopanels.runtime;

import dev.bwmp.holopanels.model.BoardDefinition;
import org.bukkit.NamespacedKey;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Concurrent because on Folia there is no single thread that owns this. Each
 * player is refreshed on the region thread ticking them, so several threads
 * reach this map at once and a plain {@code HashMap} would eventually corrupt.
 */
public final class SessionManager {
    private record SessionKey(UUID playerId, NamespacedKey boardId) {
    }

    private final Map<SessionKey, ViewerSession> sessions = new ConcurrentHashMap<>();

    public ViewerSession get(UUID playerId, BoardDefinition board) {
        return sessions.computeIfAbsent(new SessionKey(playerId, board.id()),
                ignored -> new ViewerSession(board.rootView()));
    }

    public Optional<ViewerSession> find(UUID playerId, NamespacedKey boardId) {
        return Optional.ofNullable(sessions.get(new SessionKey(playerId, boardId)));
    }

    public void remove(UUID playerId) {
        sessions.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    public void remove(NamespacedKey boardId) {
        sessions.keySet().removeIf(key -> key.boardId().equals(boardId));
    }

    public void clear() {
        sessions.clear();
    }

    /** Open viewer sessions: one per player per board they have looked at. */
    public int size() {
        return sessions.size();
    }
}
