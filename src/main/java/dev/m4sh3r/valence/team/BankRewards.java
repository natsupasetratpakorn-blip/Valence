package dev.m4sh3r.valence.team;

import dev.m4sh3r.valence.Valence;
import dev.m4sh3r.valence.util.Format;
import dev.m4sh3r.valence.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static dev.m4sh3r.valence.config.Messages.c;
import static dev.m4sh3r.valence.config.Messages.p;

/**
 * Daily bank interest and weekly rewards for the top teams. Checked every 10 minutes off the main thread.
 * On a network only the server marked as main runs this, so rewards are never paid twice.
 */
public final class BankRewards {

    private static final long DAY = TimeUnit.DAYS.toMillis(1);
    private static final long WEEK = TimeUnit.DAYS.toMillis(7);

    private final Valence plugin;
    private final File file;

    public BankRewards(Valence plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void start() {
        Tasks.asyncRepeating(this::check, 10, TimeUnit.MINUTES);
        Tasks.asyncLater(this::check, 30, TimeUnit.SECONDS);
    }

    public void check() {
        if (!plugin.settings().mainServer) {
            return;
        }
        long now = System.currentTimeMillis();
        if (plugin.settings().interestPercent > 0) {
            for (Team team : plugin.teams().teams()) {
                payInterest(team, now);
            }
        }
        if (plugin.settings().weeklyEnabled) {
            weekly(now);
        }
    }

    private void payInterest(Team team, long now) {
        double amount;
        synchronized (plugin.teams()) {
            if (now - team.lastInterest() < DAY) {
                return;
            }
            team.lastInterest(now);
            amount = Math.min(team.bank() * plugin.settings().interestPercent / 100.0, plugin.settings().interestMax);
            amount = Math.floor(amount * 100) / 100;
            if (amount <= 0) {
                team.markDirty();
                return;
            }
            team.bank(team.bank() + amount);
            team.log("The bank earned " + Format.money(amount) + " interest", plugin.settings().logSize);
        }
        plugin.teams().broadcast(team, plugin.messages().chat("bank.interest", p("amount", Format.money(amount))), null);
        plugin.teams().writeNow(team);
    }

    private synchronized void weekly(long now) {
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        long last = data.getLong("last-weekly", 0);
        if (last == 0) {
            save(data, now);
            return;
        }
        if (now - last < WEEK) {
            return;
        }
        save(data, now);
        List<Double> rewards = plugin.settings().weeklyRewards;
        List<Team> top = plugin.teams().topTeams();
        for (int i = 0; i < Math.min(rewards.size(), top.size()); i++) {
            Team team = top.get(i);
            double reward = rewards.get(i);
            synchronized (plugin.teams()) {
                team.bank(team.bank() + reward);
                team.log("Won " + Format.money(reward) + " for place " + (i + 1) + " in the weekly Team Top", plugin.settings().logSize);
            }
            plugin.teams().writeNow(team);
            Bukkit.broadcast(plugin.messages().chat("bank.weekly",
                    p("position", i + 1), c("team", team.displayName()), p("amount", Format.money(reward))));
        }
    }

    private void save(YamlConfiguration data, long now) {
        data.set("last-weekly", now);
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }
}
