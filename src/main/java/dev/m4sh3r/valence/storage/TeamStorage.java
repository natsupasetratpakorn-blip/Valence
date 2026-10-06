package dev.m4sh3r.valence.storage;

import dev.m4sh3r.valence.config.Settings;
import dev.m4sh3r.valence.team.LogEntry;
import dev.m4sh3r.valence.team.Team;
import dev.m4sh3r.valence.team.TeamHome;
import dev.m4sh3r.valence.team.TeamMember;
import dev.m4sh3r.valence.team.TeamPermission;
import dev.m4sh3r.valence.team.TeamRank;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public final class TeamStorage {

    private static final int DATA_VERSION = 2;

    private final Plugin plugin;
    private final Settings settings;
    private final Backend backend;
    private final java.util.logging.Logger logger;

    public TeamStorage(Plugin plugin, Settings settings, Backend backend) {
        this.plugin = plugin;
        this.settings = settings;
        this.backend = backend;
        this.logger = plugin != null ? plugin.getLogger() : java.util.logging.Logger.getLogger("Valence");
    }

    public List<Team> loadAll() {
        List<Team> teams = new ArrayList<>();
        List<String> texts;
        try {
            texts = backend.loadAll();
        } catch (Exception e) {
            throw new IllegalStateException("Could not load teams from storage", e);
        }
        for (String text : texts) {
            Team team = parse(text);
            if (team != null) {
                teams.add(team);
            }
        }
        return teams;
    }

    /**
     * Reads a team from saved text, or returns null when the text is broken.
     */
    public Team parse(String text) {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(text);
            return read(yaml);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not read a saved team", e);
            return null;
        }
    }

    public Backend backend() {
        return backend;
    }

    /**
     * Turns a team into yaml text. Call this while holding the team lock, then write the result off thread.
     */
    public String snapshot(Team team) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", team.id().toString());
        yaml.set("name", team.name());
        yaml.set("tag", team.tag());
        yaml.set("color", team.color());
        yaml.set("icon", team.icon().name());
        yaml.set("owner", team.owner().toString());
        yaml.set("created", team.createdAt());
        yaml.set("level", team.level());
        yaml.set("bank", team.bank());
        yaml.set("open", team.open());
        yaml.set("data-version", DATA_VERSION);
        for (int i = 1; i <= Team.MAX_HOMES; i++) {
            writeLocation(yaml, "homes." + i, team.home(i));
        }
        writeLocation(yaml, "waypoint", team.waypoint());
        yaml.set("waypoint-name", team.waypointName());
        yaml.set("allies", team.allies().stream().map(UUID::toString).toList());
        yaml.set("last-interest", team.lastInterest());
        for (TeamRank rank : team.ranks().values()) {
            String path = "ranks." + rank.id();
            yaml.set(path + ".name", rank.name());
            yaml.set(path + ".icon", rank.icon().name());
            yaml.set(path + ".weight", rank.weight());
            yaml.set(path + ".permissions", rank.permissions().stream().map(TeamPermission::key).toList());
        }
        for (TeamMember member : team.members().values()) {
            String path = "members." + member.uuid();
            yaml.set(path + ".name", member.name());
            yaml.set(path + ".rank", member.rankId());
            yaml.set(path + ".joined", member.joinedAt());
            yaml.set(path + ".kills", member.kills());
            yaml.set(path + ".deaths", member.deaths());
            yaml.set(path + ".deposited", member.deposited());
            yaml.set(path + ".friendly-fire", member.friendlyFire());
            if (member.skin() != null) {
                yaml.set(path + ".skin", member.skin());
                yaml.set(path + ".skin-signature", member.skinSignature());
            }
        }
        yaml.set("chest", team.chest());
        List<String> log = new ArrayList<>();
        for (LogEntry entry : team.log()) {
            log.add(entry.time() + "|" + entry.text());
        }
        yaml.set("log", log);
        return yaml.saveToString();
    }

    private final AtomicLong versions = new AtomicLong();
    private final Map<UUID, Long> written = new ConcurrentHashMap<>();

    /**
     * Take this together with the snapshot, while the team is locked.
     */
    public long nextVersion() {
        return versions.incrementAndGet();
    }

    /**
     * Saves run on background threads and can finish out of order. The version makes sure an older
     * snapshot never replaces a newer file, and a deleted team never comes back.
     */
    public synchronized void write(UUID id, String data, long version) {
        Long last = written.get(id);
        if (last != null && last >= version) {
            return;
        }
        written.put(id, version);
        write(id, data);
    }

    private void write(UUID id, String data) {
        try {
            backend.save(id, data);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Could not save team " + id, e);
        }
    }

    public synchronized void delete(UUID id) {
        written.put(id, Long.MAX_VALUE);
        try {
            backend.delete(id);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Could not delete team " + id, e);
        }
    }

    /**
     * A team another server changed or deleted. Our own later saves must not be blocked by its version.
     */
    public synchronized void forgetVersion(UUID id) {
        written.remove(id);
    }

    private Team read(YamlConfiguration yaml) {
        String idText = yaml.getString("id");
        String ownerText = yaml.getString("owner");
        if (idText == null || ownerText == null) {
            return null;
        }
        String colorName = yaml.getString("color", settings.defaultColor);
        Team team = new Team(UUID.fromString(idText),
                yaml.getString("name", "Team"),
                yaml.getString("tag", "TEAM"),
                colorName,
                Settings.material(yaml.getString("icon"), settings.defaultIcon),
                UUID.fromString(ownerText),
                yaml.getLong("created", System.currentTimeMillis()));
        team.color(colorName, settings.color(colorName));
        team.level(Math.max(1, yaml.getInt("level", 1)));
        team.bank(yaml.getDouble("bank"));
        team.open(yaml.getBoolean("open"));
        // Files from 1.0 had a single "home".
        team.home(1, readLocation(yaml, "home"));
        for (int i = 1; i <= Team.MAX_HOMES; i++) {
            TeamHome home = readLocation(yaml, "homes." + i);
            if (home != null) {
                team.home(i, home);
            }
        }
        team.waypoint(readLocation(yaml, "waypoint"), yaml.getString("waypoint-name", ""));
        for (String ally : yaml.getStringList("allies")) {
            try {
                team.allies().add(UUID.fromString(ally));
            } catch (IllegalArgumentException ignored) {
            }
        }
        team.lastInterest(yaml.getLong("last-interest", System.currentTimeMillis()));

        ConfigurationSection ranks = yaml.getConfigurationSection("ranks");
        if (ranks != null) {
            for (String id : ranks.getKeys(false)) {
                TeamRank rank = new TeamRank(id,
                        ranks.getString(id + ".name", id),
                        Settings.material(ranks.getString(id + ".icon"), Material.PAPER),
                        ranks.getInt(id + ".weight"));
                for (String key : ranks.getStringList(id + ".permissions")) {
                    TeamPermission permission = TeamPermission.fromKey(key);
                    if (permission != null) {
                        rank.permissions().add(permission);
                    }
                }
                team.ranks().put(id, rank);
            }
        }
        ensureBuiltInRanks(team);
        boolean migrated = false;
        if (yaml.getInt("data-version", 1) < 2) {
            // The ender chest arrived after these ranks were made, so give it to every rank once.
            for (TeamRank rank : team.ranks().values()) {
                rank.permissions().add(TeamPermission.ENDER_CHEST);
            }
            migrated = true;
        }

        ConfigurationSection members = yaml.getConfigurationSection("members");
        if (members != null) {
            for (String key : members.getKeys(false)) {
                TeamMember member = new TeamMember(UUID.fromString(key),
                        members.getString(key + ".name", "Unknown"),
                        members.getString(key + ".rank", TeamRank.MEMBER),
                        members.getLong(key + ".joined", team.createdAt()));
                member.kills(members.getInt(key + ".kills"));
                member.deaths(members.getInt(key + ".deaths"));
                member.deposited(members.getDouble(key + ".deposited"));
                member.friendlyFire(members.getBoolean(key + ".friendly-fire"));
                member.skin(members.getString(key + ".skin"), members.getString(key + ".skin-signature"));
                team.members().put(member.uuid(), member);
            }
        }
        if (!team.members().containsKey(team.owner())) {
            return null;
        }

        team.chest(yaml.getStringList("chest"));
        for (String line : yaml.getStringList("log")) {
            int split = line.indexOf('|');
            if (split > 0) {
                try {
                    team.log().addLast(new LogEntry(Long.parseLong(line.substring(0, split)), line.substring(split + 1)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (migrated) {
            team.markDirty();
        } else {
            team.clean();
        }
        return team;
    }

    private static void writeLocation(YamlConfiguration yaml, String path, TeamHome home) {
        if (home == null) {
            return;
        }
        yaml.set(path + ".world", home.world());
        yaml.set(path + ".x", home.x());
        yaml.set(path + ".y", home.y());
        yaml.set(path + ".z", home.z());
        yaml.set(path + ".yaw", home.yaw());
        yaml.set(path + ".pitch", home.pitch());
    }

    private static TeamHome readLocation(YamlConfiguration yaml, String path) {
        if (!yaml.contains(path + ".world")) {
            return null;
        }
        return new TeamHome(yaml.getString(path + ".world"),
                yaml.getDouble(path + ".x"), yaml.getDouble(path + ".y"), yaml.getDouble(path + ".z"),
                (float) yaml.getDouble(path + ".yaw"), (float) yaml.getDouble(path + ".pitch"));
    }

    public static void ensureBuiltInRanks(Team team) {
        if (!team.ranks().containsKey(TeamRank.OWNER)) {
            team.ranks().put(TeamRank.OWNER, new TeamRank(TeamRank.OWNER, "Owner", Material.GOLDEN_HELMET, TeamRank.OWNER_WEIGHT));
        }
        if (!team.ranks().containsKey(TeamRank.MEMBER)) {
            TeamRank member = new TeamRank(TeamRank.MEMBER, "Member", Material.LEATHER_HELMET, TeamRank.MEMBER_WEIGHT);
            member.permissions().add(TeamPermission.DEPOSIT);
            member.permissions().add(TeamPermission.USE_HOME);
            member.permissions().add(TeamPermission.ENDER_CHEST);
            team.ranks().put(TeamRank.MEMBER, member);
        }
        team.rank(TeamRank.OWNER).weight(TeamRank.OWNER_WEIGHT);
        team.rank(TeamRank.MEMBER).weight(TeamRank.MEMBER_WEIGHT);
    }
}
