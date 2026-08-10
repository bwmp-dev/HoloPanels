package dev.aether.holopanels.runtime;

import dev.aether.holopanels.model.BoardDefinition;
import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SessionManager {
    private record SessionKey(UUID playerId, NamespacedKey boardId) {
    }

    private final Map<SessionKey, ViewerSession> sessions = new HashMap<>();

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
}
