package dev.m4sh3r.valence;

import dev.m4sh3r.valence.chest.PendingItems;
import dev.m4sh3r.valence.chest.TeamChests;
import dev.m4sh3r.valence.team.LevelBuffs;
import dev.m4sh3r.valence.team.Waypoints;
import dev.m4sh3r.valence.team.Nametags;
import dev.m4sh3r.valence.team.BankRewards;
import dev.m4sh3r.valence.command.AllyChatCommand;
import dev.m4sh3r.valence.command.TeamChatCommand;
import dev.m4sh3r.valence.command.TeamCommand;
import dev.m4sh3r.valence.config.Messages;
import dev.m4sh3r.valence.config.Settings;
import dev.m4sh3r.valence.economy.Money;
import dev.m4sh3r.valence.economy.VaultMoney;
import dev.m4sh3r.valence.hook.ValenceExpansion;
import dev.m4sh3r.valence.listener.PlayerListener;
import dev.m4sh3r.valence.menu.ChatMenus;
import dev.m4sh3r.valence.menu.MenuFactory;
import dev.m4sh3r.valence.menu.Menus;
import dev.m4sh3r.valence.storage.Backend;
import dev.m4sh3r.valence.storage.FileBackend;
import dev.m4sh3r.valence.storage.MysqlBackend;
import dev.m4sh3r.valence.storage.TeamStorage;
import dev.m4sh3r.valence.team.TeamManager;
import dev.m4sh3r.valence.team.TeamPrefix;
import dev.m4sh3r.valence.util.Format;
import dev.m4sh3r.valence.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

public final class Valence extends JavaPlugin {

    private final Settings settings = new Settings();
    private Messages messages;
    private TeamStorage storage;
    private TeamManager teams;
    private Menus menus;
    private ChatMenus chatViews;
    private TeamChests chests;
    private PendingItems pending;
    private LevelBuffs buffs;
    private Waypoints waypoints;
    private Nametags nametags;
    private TeamPrefix prefixes;
    private Money money = Money.NONE;

    @Override
    public void onEnable() {
        Tasks.init(this);
        saveDefaultConfig();
        settings.load(getConfig());
        Format.setup(settings.currencySymbol, settings.dateFormat);
        messages = new Messages(this, settings);
        messages.load();

        Backend backend = createBackend();
        storage = new TeamStorage(this, settings, backend);
        teams = new TeamManager(this);
        teams.load(storage.loadAll());

        menus = MenuFactory.create(this);
        chatViews = menus instanceof ChatMenus chat ? chat : new ChatMenus(this);
        getLogger().info(MenuFactory.dialogsSupported()
                ? "Dialog menus are on."
                : "This server has no dialog API (needs 1.21.7+). Using chat menus.");

        PluginCommand team = getCommand("team");
        if (team != null) {
            TeamCommand executor = new TeamCommand(this);
            team.setExecutor(executor);
            team.setTabCompleter(executor);
        }
        PluginCommand allyChat = getCommand("allychat");
        if (allyChat != null) {
            allyChat.setExecutor(new AllyChatCommand(this));
        }
        PluginCommand teamChat = getCommand("teamchat");
        if (teamChat != null) {
            teamChat.setExecutor(new TeamChatCommand(this));
        }
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        nametags = new Nametags(this);
        prefixes = new TeamPrefix(this);
        Bukkit.getPluginManager().registerEvents(prefixes, this);
        pending = new PendingItems(this);
        Bukkit.getPluginManager().registerEvents(pending, this);
        buffs = new LevelBuffs(this);
        Bukkit.getPluginManager().registerEvents(buffs, this);
        buffs.startAll();
        waypoints = new Waypoints(this);
        Bukkit.getPluginManager().registerEvents(waypoints, this);
        chests = new TeamChests(this);
        Bukkit.getPluginManager().registerEvents(chests, this);

        // Economy plugins often register late, so hook once the server is fully started.
        Tasks.global(this::hookEconomy);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new ValenceExpansion(this).register();
        }

        new BankRewards(this).start();
        Tasks.global(() -> prefixes.reload());
        if (backend.networked()) {
            // Push our changes first, then pull everyone else's.
            Tasks.asyncRepeating(() -> {
                teams.saveDirty();
                teams.syncFromNetwork();
            }, settings.syncSeconds, TimeUnit.SECONDS);
        }
        Tasks.asyncRepeating(teams::saveDirty, settings.autosaveMinutes, TimeUnit.MINUTES);
        getLogger().info("Loaded " + teams.teams().size() + " teams.");
    }

    @Override
    public void onDisable() {
        Tasks.cancelAll();
        if (chests != null) {
            chests.saveOpen();
        }
        if (teams != null) {
            teams.saveAll();
        }
        if (nametags != null) {
            nametags.clearAll();
        }
        if (storage != null) {
            storage.backend().close();
        }
    }

    private Backend createBackend() {
        if (settings.storageType.equalsIgnoreCase("mysql")) {
            try {
                Backend mysql = new MysqlBackend(settings.mysqlHost, settings.mysqlPort, settings.mysqlDatabase,
                        settings.mysqlUser, settings.mysqlPassword, settings.mysqlSsl, settings.mysqlPrefix, serverId());
                getLogger().info("Using MySQL storage.");
                return mysql;
            } catch (Exception e) {
                getLogger().severe("Could not connect to MySQL, using files instead: " + e.getMessage());
            }
        }
        return new FileBackend(new java.io.File(getDataFolder(), "teams"));
    }

    // A random id for this server, kept between restarts, so it can tell its own database writes apart.
    private String serverId() {
        java.io.File file = new java.io.File(getDataFolder(), "server-id.txt");
        try {
            if (file.exists()) {
                String id = java.nio.file.Files.readString(file.toPath()).trim();
                if (id.length() == 36) {
                    return id;
                }
            }
            String id = java.util.UUID.randomUUID().toString();
            java.nio.file.Files.writeString(file.toPath(), id);
            return id;
        } catch (java.io.IOException e) {
            return java.util.UUID.randomUUID().toString();
        }
    }

    private void hookEconomy() {
        // Checks for the Vault API itself, so Folia forks like VaultUnlocked work too.
        try {
            Class.forName("net.milkbowl.vault.economy.Economy");
        } catch (ClassNotFoundException e) {
            getLogger().info("Vault not found. The team bank is turned off.");
            return;
        }
        Money vault = VaultMoney.hook();
        if (vault == null) {
            getLogger().info("Vault has no economy plugin. The team bank is turned off.");
            return;
        }
        money = vault;
        getLogger().info("Hooked into Vault economy.");
    }

    public void reload() {
        reloadConfig();
        settings.load(getConfig());
        Format.setup(settings.currencySymbol, settings.dateFormat);
        messages.load();
        teams.refreshColors();
        prefixes.reload();
        if (!money.enabled()) {
            hookEconomy();
        }
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public TeamStorage storage() {
        return storage;
    }

    public TeamManager teams() {
        return teams;
    }

    public TeamPrefix prefixes() {
        return prefixes;
    }

    public Nametags nametags() {
        return nametags;
    }

    public Waypoints waypoints() {
        return waypoints;
    }

    public PendingItems pending() {
        return pending;
    }

    public TeamChests chests() {
        return chests;
    }

    public ChatMenus chatViews() {
        return chatViews;
    }

    public Menus menus() {
        return menus;
    }

    public Money money() {
        return money;
    }
}
