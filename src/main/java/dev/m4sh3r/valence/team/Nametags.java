package dev.m4sh3r.valence.team;

import dev.m4sh3r.valence.Valence;
import dev.m4sh3r.valence.util.Tasks;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

import java.util.UUID;

/**
 * Shows the team prefix above player heads through scoreboard teams on the main scoreboard.
 * Folia has no scoreboard API, so this turns itself off there.
 */
public final class Nametags {

    private static final String PREFIX = "valence_";

    private final Valence plugin;
    private final boolean available;

    public Nametags(Valence plugin) {
        this.plugin = plugin;
        boolean ok;
        try {
            // Folia hands out a scoreboard but refuses every change to it.
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            ok = false;
        } catch (ClassNotFoundException e) {
            ok = true;
        }
        this.available = ok;
        if (!ok && plugin.settings().prefixNametag) {
            plugin.getLogger().info("This server has no scoreboard API (Folia), so prefixes above heads are off.");
        }
    }

    private boolean enabled() {
        return available && plugin.settings().prefixNametag;
    }

    private static String id(UUID teamId) {
        return PREFIX + teamId.toString().replace("-", "").substring(0, 8);
    }

    public void update(Team team) {
        if (!enabled()) {
            return;
        }
        Tasks.global(() -> {
            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            org.bukkit.scoreboard.Team sb = board.getTeam(id(team.id()));
            if (sb == null) {
                sb = board.registerNewTeam(id(team.id()));
            }
            sb.prefix(plugin.prefixes().of(team));
            sb.color(NamedTextColor.WHITE);
            sb.setAllowFriendlyFire(true);
            for (TeamMember member : team.members().values()) {
                if (!sb.hasEntry(member.name())) {
                    sb.addEntry(member.name());
                }
            }
        });
    }

    public void player(UUID uuid) {
        if (!enabled()) {
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        String name = player.getName();
        Tasks.global(() -> {
            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = plugin.teams().teamOf(uuid);
            org.bukkit.scoreboard.Team current = board.getEntryTeam(name);
            if (current != null && current.getName().startsWith(PREFIX)
                    && (team == null || !current.getName().equals(id(team.id())))) {
                current.removeEntry(name);
            }
            if (team != null) {
                update(team);
            }
        });
    }

    public void remove(Team team) {
        if (!enabled()) {
            return;
        }
        Tasks.global(() -> {
            org.bukkit.scoreboard.Team sb = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(id(team.id()));
            if (sb != null) {
                sb.unregister();
            }
        });
    }

    public void clearAll() {
        if (!available) {
            return;
        }
        for (org.bukkit.scoreboard.Team sb : Bukkit.getScoreboardManager().getMainScoreboard().getTeams()) {
            if (sb.getName().startsWith(PREFIX)) {
                sb.unregister();
            }
        }
    }
}
