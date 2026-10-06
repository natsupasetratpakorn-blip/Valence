package dev.m4sh3r.valence.config;

import dev.m4sh3r.valence.team.Team;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class Settings {

    public record Level(int level, double cost, int maxMembers, int chestRows, int homes,
                        org.bukkit.potion.PotionEffectType buff, int buffAmplifier) {
    }

    public int nameMin;
    public int nameMax;
    public int tagMin;
    public int tagMax;
    public int inviteExpireSeconds;
    public Material defaultIcon;
    public String defaultColor;
    public List<String> blockedNames;

    public final TreeMap<Integer, Level> levels = new TreeMap<>();

    public String currencySymbol;
    public double minDeposit;
    public int logSize;

    public boolean chestEnabled;
    public int chestUnlockLevel;

    public boolean homeEnabled;
    public int buffRadius;
    public int homeWarmup;
    public int homeCooldown;

    public String prefixFormat;
    public boolean prefixChat;
    public boolean prefixTab;
    public boolean prefixNametag;

    public double interestPercent;
    public double interestMax;
    public boolean weeklyEnabled;
    public List<Double> weeklyRewards = List.of();
    public boolean mainServer;
    public String storageType;
    public String mysqlHost;
    public int mysqlPort;
    public String mysqlDatabase;
    public String mysqlUser;
    public String mysqlPassword;
    public boolean mysqlSsl;
    public String mysqlPrefix;
    public int syncSeconds;

    public boolean smallCaps;
    public String teamChatFormat;
    public String allyChatFormat;
    public int maxAllies;

    public Material noTeamIcon;
    public String dateFormat;
    public int leaderboardSize;

    public int autosaveMinutes;

    public final Map<String, TextColor> colors = new LinkedHashMap<>();
    public final List<Material> icons = new ArrayList<>();

    public void load(FileConfiguration config) {
        nameMin = config.getInt("team.name-length.min", 3);
        nameMax = config.getInt("team.name-length.max", 16);
        tagMin = config.getInt("team.tag-length.min", 2);
        tagMax = config.getInt("team.tag-length.max", 5);
        inviteExpireSeconds = config.getInt("team.invite-expire-seconds", 120);
        defaultIcon = material(config.getString("team.default-icon"), Material.EMERALD);
        defaultColor = config.getString("team.default-color", "Aqua");
        blockedNames = config.getStringList("team.blocked-names").stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();

        levels.clear();
        ConfigurationSection levelSection = config.getConfigurationSection("levels");
        if (levelSection != null) {
            for (String key : levelSection.getKeys(false)) {
                try {
                    int level = Integer.parseInt(key);
                    levels.put(level, new Level(level,
                            levelSection.getDouble(key + ".cost"),
                            levelSection.getInt(key + ".max-members", 4),
                            Math.max(1, Math.min(6, levelSection.getInt(key + ".chest-rows", 3))),
                            Math.max(0, Math.min(Team.MAX_HOMES, levelSection.getInt(key + ".homes", 1))),
                            effect(levelSection.getString(key + ".buff.effect")),
                            Math.max(0, levelSection.getInt(key + ".buff.level", 1) - 1)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (levels.isEmpty()) {
            levels.put(1, new Level(1, 0, 4, 3, 1, null, 0));
        }

        currencySymbol = config.getString("bank.currency-symbol", "$");
        minDeposit = config.getDouble("bank.min-deposit", 10);
        logSize = config.getInt("bank.log-size", 50);

        chestEnabled = config.getBoolean("ender-chest.enabled", true);
        chestUnlockLevel = config.getInt("ender-chest.unlock-level", 1);

        homeEnabled = config.getBoolean("home.enabled", true);
        buffRadius = Math.max(1, config.getInt("level-buff-radius", 24));
        homeWarmup = config.getInt("home.warmup-seconds", 3);
        homeCooldown = config.getInt("home.cooldown-seconds", 30);

        prefixFormat = config.getString("prefix.format", "<team_icon> <team_color><team_tag></team_color> ");
        prefixChat = config.getBoolean("prefix.chat", true);
        prefixTab = config.getBoolean("prefix.tab-list", true);
        prefixNametag = config.getBoolean("prefix.above-head", true);

        interestPercent = Math.max(0, config.getDouble("bank.daily-interest.percent", 1.0));
        interestMax = Math.max(0, config.getDouble("bank.daily-interest.max", 5000));
        weeklyEnabled = config.getBoolean("weekly-rewards.enabled", true);
        weeklyRewards = config.getDoubleList("weekly-rewards.top-teams");
        mainServer = config.getBoolean("storage.main-server", true);
        storageType = config.getString("storage.type", "file");
        mysqlHost = config.getString("storage.mysql.host", "localhost");
        mysqlPort = config.getInt("storage.mysql.port", 3306);
        mysqlDatabase = config.getString("storage.mysql.database", "valence");
        mysqlUser = config.getString("storage.mysql.user", "root");
        mysqlPassword = config.getString("storage.mysql.password", "");
        mysqlSsl = config.getBoolean("storage.mysql.ssl", false);
        mysqlPrefix = config.getString("storage.mysql.table-prefix", "valence_");
        syncSeconds = Math.max(2, config.getInt("storage.mysql.sync-seconds", 5));

        smallCaps = config.getBoolean("chat.small-caps", true);
        teamChatFormat = config.getString("chat.team-chat-format", "[<team_tag>] <player>: <message>");
        allyChatFormat = config.getString("chat.ally-chat-format", "[Ally] [<team_tag>] <player>: <message>");
        maxAllies = Math.max(0, config.getInt("allies.max", 3));

        noTeamIcon = material(config.getString("menu.no-team-icon"), Material.EMERALD);
        dateFormat = config.getString("menu.date-format", "MMM d, yyyy");
        leaderboardSize = config.getInt("menu.leaderboard-size", 10);

        autosaveMinutes = Math.max(1, config.getInt("autosave-minutes", 5));

        colors.clear();
        ConfigurationSection colorSection = config.getConfigurationSection("colors");
        if (colorSection != null) {
            for (String key : colorSection.getKeys(false)) {
                TextColor color = TextColor.fromHexString(colorSection.getString(key, ""));
                if (color != null) {
                    colors.put(key, color);
                }
            }
        }
        if (colors.isEmpty()) {
            colors.put("Aqua", TextColor.color(0x3BC9DB));
        }
        if (!colors.containsKey(defaultColor)) {
            defaultColor = colors.keySet().iterator().next();
        }

        icons.clear();
        for (String name : config.getStringList("icons")) {
            Material material = material(name, null);
            if (material != null && isItem(material) && !icons.contains(material)) {
                icons.add(material);
            }
        }
        if (!icons.contains(defaultIcon)) {
            icons.add(0, defaultIcon);
        }
    }

    public Level level(int level) {
        Map.Entry<Integer, Level> entry = levels.floorEntry(level);
        return entry != null ? entry.getValue() : levels.firstEntry().getValue();
    }

    public Level nextLevel(int level) {
        Map.Entry<Integer, Level> entry = levels.higherEntry(level);
        return entry != null ? entry.getValue() : null;
    }

    public TextColor color(String name) {
        TextColor color = colors.get(name);
        if (color == null) {
            color = TextColor.fromHexString(name);
        }
        return color != null ? color : colors.get(defaultColor);
    }

    public String colorName(String name) {
        for (String key : colors.keySet()) {
            if (key.equalsIgnoreCase(name)) {
                return key;
            }
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static org.bukkit.potion.PotionEffectType effect(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            org.bukkit.potion.PotionEffectType type = org.bukkit.Registry.EFFECT.get(
                    org.bukkit.NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT).replace("minecraft:", "")));
            return type != null ? type : org.bukkit.potion.PotionEffectType.getByName(name);
        } catch (Throwable e) {
            // No server around, like in tests.
            return null;
        }
    }

    public static Material material(String name, Material fallback) {
        if (name == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(name);
        return material != null && isItem(material) ? material : fallback;
    }

    private static boolean isItem(Material material) {
        try {
            return material.isItem();
        } catch (Throwable e) {
            // No server around, like in tests. Assume the config is right.
            return !material.name().endsWith("AIR");
        }
    }
}
