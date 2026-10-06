package dev.m4sh3r.valence.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Teams in a MySQL or MariaDB database, shared by every server on a network.
 * Each server polls for changes made by the others, and team ender chests are locked to one server at a time.
 */
public final class MysqlBackend implements Backend {

    private static final long LOCK_MILLIS = TimeUnit.MINUTES.toMillis(30);

    private final String url;
    private final String user;
    private final String password;
    private final String teams;
    private final String locks;
    private final String pending;
    private final String serverId;
    private Connection connection;
    private long lastPoll;

    public MysqlBackend(String host, int port, String database, String user, String password, boolean ssl,
                        String tablePrefix, String serverId) throws SQLException {
        this.url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + ssl + "&allowPublicKeyRetrieval=true&characterEncoding=utf8";
        this.user = user;
        this.password = password;
        this.teams = tablePrefix + "teams";
        this.locks = tablePrefix + "chest_locks";
        this.pending = tablePrefix + "pending_items";
        this.serverId = serverId;
        try (Statement statement = connection().createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + teams + " ("
                    + "id CHAR(36) NOT NULL PRIMARY KEY,"
                    + "data MEDIUMTEXT NULL,"
                    + "origin CHAR(36) NOT NULL,"
                    + "updated BIGINT NOT NULL,"
                    + "INDEX (updated))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + locks + " ("
                    + "team CHAR(36) NOT NULL PRIMARY KEY,"
                    + "server CHAR(36) NOT NULL,"
                    + "expires BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + pending + " ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                    + "player CHAR(36) NOT NULL,"
                    + "item MEDIUMTEXT NOT NULL,"
                    + "INDEX (player))");
        }
        lastPoll = now();
    }

    private synchronized Connection connection() throws SQLException {
        if (connection == null || !connection.isValid(2)) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
            }
            connection = DriverManager.getConnection(url, user, password);
        }
        return connection;
    }

    // The database clock, so servers with different clocks still agree on what is newer.
    private synchronized long now() throws SQLException {
        try (Statement statement = connection().createStatement();
             ResultSet result = statement.executeQuery("SELECT ROUND(UNIX_TIMESTAMP(NOW(3)) * 1000)")) {
            result.next();
            return result.getLong(1);
        }
    }

    @Override
    public synchronized List<String> loadAll() throws SQLException {
        List<String> list = new ArrayList<>();
        try (Statement statement = connection().createStatement();
             ResultSet result = statement.executeQuery("SELECT data FROM " + teams + " WHERE data IS NOT NULL")) {
            while (result.next()) {
                list.add(result.getString(1));
            }
        }
        return list;
    }

    @Override
    public synchronized String load(UUID id) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("SELECT data FROM " + teams + " WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }

    @Override
    public synchronized void save(UUID id, String data) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("INSERT INTO " + teams
                + " (id, data, origin, updated) VALUES (?, ?, ?, ROUND(UNIX_TIMESTAMP(NOW(3)) * 1000))"
                + " ON DUPLICATE KEY UPDATE data = VALUES(data), origin = VALUES(origin), updated = VALUES(updated)")) {
            statement.setString(1, id.toString());
            statement.setString(2, data);
            statement.setString(3, serverId);
            statement.executeUpdate();
        }
    }

    @Override
    public synchronized void delete(UUID id) throws SQLException {
        // Kept as an empty row so other servers notice the delete.
        save(id, null);
    }

    @Override
    public synchronized Map<UUID, String> changes() throws SQLException {
        Map<UUID, String> changes = new HashMap<>();
        long since = lastPoll - 2000;
        long newest = lastPoll;
        try (PreparedStatement statement = connection().prepareStatement(
                "SELECT id, data, updated FROM " + teams + " WHERE updated > ? AND origin <> ? ORDER BY updated")) {
            statement.setLong(1, since);
            statement.setString(2, serverId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    changes.put(UUID.fromString(result.getString(1)), result.getString(2));
                    newest = Math.max(newest, result.getLong(3));
                }
            }
        }
        lastPoll = newest;
        return changes;
    }

    @Override
    public boolean networked() {
        return true;
    }

    @Override
    public synchronized boolean lockChest(UUID team) throws SQLException {
        Connection con = connection();
        boolean auto = con.getAutoCommit();
        con.setAutoCommit(false);
        try {
            long now = now();
            String holder = null;
            long expires = 0;
            try (PreparedStatement select = con.prepareStatement("SELECT server, expires FROM " + locks + " WHERE team = ? FOR UPDATE")) {
                select.setString(1, team.toString());
                try (ResultSet result = select.executeQuery()) {
                    if (result.next()) {
                        holder = result.getString(1);
                        expires = result.getLong(2);
                    }
                }
            }
            if (holder != null && !holder.equals(serverId) && expires > now) {
                con.rollback();
                return false;
            }
            try (PreparedStatement upsert = con.prepareStatement("INSERT INTO " + locks + " (team, server, expires) VALUES (?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE server = VALUES(server), expires = VALUES(expires)")) {
                upsert.setString(1, team.toString());
                upsert.setString(2, serverId);
                upsert.setLong(3, now + LOCK_MILLIS);
                upsert.executeUpdate();
            }
            con.commit();
            return true;
        } catch (SQLException e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(auto);
        }
    }

    @Override
    public synchronized void unlockChest(UUID team) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("DELETE FROM " + locks + " WHERE team = ? AND server = ?")) {
            statement.setString(1, team.toString());
            statement.setString(2, serverId);
            statement.executeUpdate();
        }
    }

    @Override
    public synchronized boolean addPending(UUID player, List<String> items) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement("INSERT INTO " + pending + " (player, item) VALUES (?, ?)")) {
            for (String item : items) {
                statement.setString(1, player.toString());
                statement.setString(2, item);
                statement.addBatch();
            }
            statement.executeBatch();
        }
        return true;
    }

    // Read and delete in one transaction, so two servers can never hand out the same items.
    @Override
    public synchronized List<String> takePending(UUID player) throws SQLException {
        Connection con = connection();
        boolean auto = con.getAutoCommit();
        con.setAutoCommit(false);
        try {
            List<String> items = new ArrayList<>();
            List<Long> ids = new ArrayList<>();
            try (PreparedStatement select = con.prepareStatement("SELECT id, item FROM " + pending + " WHERE player = ? FOR UPDATE")) {
                select.setString(1, player.toString());
                try (ResultSet result = select.executeQuery()) {
                    while (result.next()) {
                        ids.add(result.getLong(1));
                        items.add(result.getString(2));
                    }
                }
            }
            try (PreparedStatement delete = con.prepareStatement("DELETE FROM " + pending + " WHERE id = ?")) {
                for (long id : ids) {
                    delete.setLong(1, id);
                    delete.addBatch();
                }
                delete.executeBatch();
            }
            con.commit();
            return items;
        } catch (SQLException e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(auto);
        }
    }

    @Override
    public synchronized void close() {
        if (connection != null) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + locks + " WHERE server = ?")) {
                statement.setString(1, serverId);
                statement.executeUpdate();
            } catch (SQLException ignored) {
            }
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
