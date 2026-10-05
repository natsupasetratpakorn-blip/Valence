package dev.m4sh3r.valence.config;

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

    public record Level(int level, double cost, int maxMembers) {
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
    public int chestRows;
    public int chestUnlockLevel;

    public boolean homeEnabled;
    public int homeUnlockLevel;
    public int homeWarmup;
    public int homeCooldown;

    public String prefixFormat;
    public boolean prefixChat;
    public boolean prefixTab;

    public boolean smallCaps;
    public String teamChatFormat;

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
                            levelSection.getInt(key + ".max-members", 4)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (levels.isEmpty()) {
            levels.put(1, new Level(1, 0, 4));
        }

        currencySymbol = config.getString("bank.currency-symbol", "$");
        minDeposit = config.getDouble("bank.min-deposit", 10);
        logSize = config.getInt("bank.log-size", 50);

        chestEnabled = config.getBoolean("ender-chest.enabled", true);
        chestRows = Math.max(1, Math.min(6, config.getInt("ender-chest.rows", 3)));
        chestUnlockLevel = config.getInt("ender-chest.unlock-level", 1);

        homeEnabled = config.getBoolean("home.enabled", true);
        homeUnlockLevel = config.getInt("home.unlock-level", 1);
        homeWarmup = config.getInt("home.warmup-seconds", 3);
        homeCooldown = config.getInt("home.cooldown-seconds", 30);

        prefixFormat = config.getString("prefix.format", "<team_icon> <team_color><team_tag></team_color> ");
        prefixChat = config.getBoolean("prefix.chat", true);
        prefixTab = config.getBoolean("prefix.tab-list", true);

        smallCaps = config.getBoolean("chat.small-caps", true);
        teamChatFormat = config.getString("chat.team-chat-format", "[<team_tag>] <player>: <message>");

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
            if (material != null && material.isItem() && !icons.contains(material)) {
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

    public static Material material(String name, Material fallback) {
        if (name == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(name);
        return material != null && material.isItem() ? material : fallback;
    }
}
