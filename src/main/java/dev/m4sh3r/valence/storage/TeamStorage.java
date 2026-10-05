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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public final class TeamStorage {

    private final Plugin plugin;
    private final Settings settings;
    private final File folder;

    public TeamStorage(Plugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.folder = new File(plugin.getDataFolder(), "teams");
    }

    public List<Team> loadAll() {
        List<Team> teams = new ArrayList<>();
        if (!folder.exists() && !folder.mkdirs()) {
            return teams;
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return teams;
        }
        for (File file : files) {
            try {
                Team team = read(YamlConfiguration.loadConfiguration(file));
                if (team != null) {
                    teams.add(team);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Could not load team file " + file.getName(), e);
            }
        }
        return teams;
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
        TeamHome home = team.home();
        if (home != null) {
            yaml.set("home.world", home.world());
            yaml.set("home.x", home.x());
            yaml.set("home.y", home.y());
            yaml.set("home.z", home.z());
            yaml.set("home.yaw", home.yaw());
            yaml.set("home.pitch", home.pitch());
        }
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

    public void write(UUID id, String data) {
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create the teams folder.");
            return;
        }
        File target = new File(folder, id + ".yml");
        File temp = new File(folder, id + ".yml.tmp");
        try {
            Files.writeString(temp.toPath(), data);
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException second) {
                plugin.getLogger().log(Level.SEVERE, "Could not save team " + id, second);
            }
        }
    }

    public void delete(UUID id) {
        File file = new File(folder, id + ".yml");
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete team file " + file.getName());
        }
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
        if (yaml.contains("home.world")) {
            team.home(new TeamHome(yaml.getString("home.world"),
                    yaml.getDouble("home.x"), yaml.getDouble("home.y"), yaml.getDouble("home.z"),
                    (float) yaml.getDouble("home.yaw"), (float) yaml.getDouble("home.pitch")));
        }

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
        team.clean();
        return team;
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
