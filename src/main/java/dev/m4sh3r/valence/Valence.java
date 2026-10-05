package dev.m4sh3r.valence;

import dev.m4sh3r.valence.chest.TeamChests;
import dev.m4sh3r.valence.command.TeamChatCommand;
import dev.m4sh3r.valence.command.TeamCommand;
import dev.m4sh3r.valence.config.Messages;
import dev.m4sh3r.valence.config.Settings;
import dev.m4sh3r.valence.economy.Money;
import dev.m4sh3r.valence.economy.VaultMoney;
import dev.m4sh3r.valence.hook.ValenceExpansion;
import dev.m4sh3r.valence.listener.PlayerListener;
import dev.m4sh3r.valence.menu.MenuFactory;
import dev.m4sh3r.valence.menu.Menus;
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
    private TeamChests chests;
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

        storage = new TeamStorage(this, settings);
        teams = new TeamManager(this);
        teams.load(storage.loadAll());

        menus = MenuFactory.create(this);
        getLogger().info(MenuFactory.dialogsSupported()
                ? "Dialog menus are on."
                : "This server has no dialog API (needs 1.21.7+). Using chat menus.");

        PluginCommand team = getCommand("team");
        if (team != null) {
            TeamCommand executor = new TeamCommand(this);
            team.setExecutor(executor);
            team.setTabCompleter(executor);
        }
        PluginCommand teamChat = getCommand("teamchat");
        if (teamChat != null) {
            teamChat.setExecutor(new TeamChatCommand(this));
        }
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        prefixes = new TeamPrefix(this);
        Bukkit.getPluginManager().registerEvents(prefixes, this);
        chests = new TeamChests(this);
        Bukkit.getPluginManager().registerEvents(chests, this);

        // Economy plugins often register late, so hook once the server is fully started.
        Tasks.global(this::hookEconomy);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new ValenceExpansion(this).register();
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

    public TeamChests chests() {
        return chests;
    }

    public Menus menus() {
        return menus;
    }

    public Money money() {
        return money;
    }
}
