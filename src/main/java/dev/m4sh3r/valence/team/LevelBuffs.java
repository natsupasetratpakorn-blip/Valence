package dev.m4sh3r.valence.team;

import dev.m4sh3r.valence.Valence;
import dev.m4sh3r.valence.config.Settings;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;

import java.util.UUID;

/**
 * Teams with a buff in their level give it to members standing near another online teammate.
 * Each player checks for themselves every 5 seconds on their own thread, so this stays cheap and Folia safe.
 */
public final class LevelBuffs implements Listener {

    private static final long PERIOD = 100;

    private final Valence plugin;

    public LevelBuffs(Valence plugin) {
        this.plugin = plugin;
    }

    public void startAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            start(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        start(event.getPlayer());
    }

    private void start(Player player) {
        player.getScheduler().runAtFixedRate(plugin, task -> tick(player, task), null, PERIOD, PERIOD);
    }

    private void tick(Player player, ScheduledTask task) {
        if (!player.isOnline()) {
            task.cancel();
            return;
        }
        Team team = plugin.teams().teamOf(player.getUniqueId());
        if (team == null) {
            return;
        }
        Settings.Level level = plugin.settings().level(team.level());
        if (level.buff() == null || !nearTeammate(player, team)) {
            return;
        }
        player.addPotionEffect(new PotionEffect(level.buff(), (int) PERIOD + 60, level.buffAmplifier(), true, false, true));
    }

    private boolean nearTeammate(Player player, Team team) {
        Location here = player.getLocation();
        double radius = plugin.settings().buffRadius;
        double max = radius * radius;
        for (UUID uuid : team.members().keySet()) {
            if (uuid.equals(player.getUniqueId())) {
                continue;
            }
            Player other = Bukkit.getPlayer(uuid);
            if (other == null || !other.getWorld().equals(here.getWorld())) {
                continue;
            }
            if (other.getLocation().distanceSquared(here) <= max) {
                return true;
            }
        }
        return false;
    }
}
