package dev.m4sh3r.valence.team;

import dev.m4sh3r.valence.Valence;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static dev.m4sh3r.valence.config.Messages.c;
import static dev.m4sh3r.valence.config.Messages.p;

/**
 * A shared team marker. Players who follow it get a bossbar with an arrow and the distance,
 * updated on their own thread twice a second.
 */
public final class Waypoints implements Listener {

    private static final String[] ARROWS = {"⬆", "⬈", "➡", "⬊", "⬇", "⬋", "⬅", "⬉"};

    private final Valence plugin;
    private final Map<UUID, BossBar> following = new ConcurrentHashMap<>();

    public Waypoints(Valence plugin) {
        this.plugin = plugin;
    }

    public synchronized boolean set(Player actor, String name) {
        TeamManager teams = plugin.teams();
        Team team = teams.requireTeam(actor);
        if (team == null) {
            return false;
        }
        if (!team.has(actor.getUniqueId(), TeamPermission.SET_WAYPOINT)) {
            plugin.messages().send(actor, "no-team-permission");
            return false;
        }
        String clean = name == null || name.isBlank() ? "Waypoint" : name.trim();
        if (clean.length() > 24) {
            clean = clean.substring(0, 24);
        }
        synchronized (teams) {
            team.waypoint(TeamHome.of(actor.getLocation()), clean);
            team.log(actor.getName() + " set the waypoint " + clean, plugin.settings().logSize);
        }
        teams.broadcast(team, plugin.messages().chat("waypoint.set", p("player", actor.getName()), p("name", clean)), null);
        return true;
    }

    public synchronized boolean clear(Player actor) {
        TeamManager teams = plugin.teams();
        Team team = teams.requireTeam(actor);
        if (team == null) {
            return false;
        }
        if (!team.has(actor.getUniqueId(), TeamPermission.SET_WAYPOINT)) {
            plugin.messages().send(actor, "no-team-permission");
            return false;
        }
        synchronized (teams) {
            team.waypoint(null, "");
            team.markDirty();
        }
        plugin.messages().send(actor, "waypoint.cleared");
        return true;
    }

    public boolean following(UUID player) {
        return following.containsKey(player);
    }

    /**
     * Call on the player's own thread.
     */
    public void toggle(Player player) {
        BossBar bar = following.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
            plugin.messages().send(player, "waypoint.unfollow");
            return;
        }
        Team team = plugin.teams().requireTeam(player);
        if (team == null) {
            return;
        }
        if (team.waypoint() == null) {
            plugin.messages().send(player, "waypoint.none");
            return;
        }
        BossBar created = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        following.put(player.getUniqueId(), created);
        player.showBossBar(created);
        plugin.messages().send(player, "waypoint.follow", p("name", team.waypointName()));
        player.getScheduler().runAtFixedRate(plugin, task -> update(player, created, task), null, 1, 10);
    }

    private void update(Player player, BossBar bar, ScheduledTask task) {
        if (!player.isOnline() || following.get(player.getUniqueId()) != bar) {
            player.hideBossBar(bar);
            task.cancel();
            return;
        }
        Team team = plugin.teams().teamOf(player.getUniqueId());
        TeamHome waypoint = team == null ? null : team.waypoint();
        if (waypoint == null) {
            following.remove(player.getUniqueId(), bar);
            player.hideBossBar(bar);
            task.cancel();
            return;
        }
        Location here = player.getLocation();
        Location target = waypoint.toLocation();
        if (target == null || !target.getWorld().equals(here.getWorld())) {
            bar.name(plugin.messages().menu("waypoint.other-world", p("name", team.waypointName()), p("world", waypoint.world())));
            return;
        }
        double dx = target.getX() - here.getX();
        double dz = target.getZ() - here.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz + Math.pow(target.getY() - here.getY(), 2));
        // Angle of the target compared to where the player is looking, split into 8 arrows.
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = ((targetYaw - here.getYaw()) % 360 + 360 + 22.5) % 360;
        String arrow = distance < 3 ? "●" : ARROWS[(int) (relative / 45) % 8];
        bar.name(plugin.messages().menu("waypoint.bar",
                c("team", team.displayName()),
                p("arrow", arrow),
                p("name", team.waypointName()),
                p("distance", Math.round(distance))));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        following.remove(event.getPlayer().getUniqueId());
    }

    public void stop(UUID player) {
        following.remove(player);
    }
}
