package dev.m4sh3r.valence.storage;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Where team files live. Every method is called off the main thread.
 */
public interface Backend {

    List<String> loadAll() throws Exception;

    /**
     * The newest copy of one team, or null when it no longer exists.
     */
    String load(UUID id) throws Exception;

    void save(UUID id, String data) throws Exception;

    void delete(UUID id) throws Exception;

    /**
     * Teams that other servers changed since the last call. A null value means the team was deleted.
     */
    Map<UUID, String> changes() throws Exception;

    boolean networked();

    /**
     * Locks a team's ender chest to this server. Always true when there is only one server.
     */
    boolean lockChest(UUID team) throws Exception;

    void unlockChest(UUID team) throws Exception;

    /**
     * Items owed to an offline player. Returns false when this backend keeps no such list (files use their own).
     */
    default boolean addPending(UUID player, List<String> items) throws Exception {
        return false;
    }

    /**
     * Removes and returns what a player is owed, or null when this backend keeps no such list.
     */
    default List<String> takePending(UUID player) throws Exception {
        return null;
    }

    void close();
}
