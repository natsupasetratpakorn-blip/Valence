package dev.m4sh3r.valence.hook;

import dev.m4sh3r.valence.Valence;
import dev.m4sh3r.valence.team.Team;
import dev.m4sh3r.valence.team.TeamMember;
import dev.m4sh3r.valence.util.Format;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class ValenceExpansion extends PlaceholderExpansion {

    private final Valence plugin;

    public ValenceExpansion(Valence plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "valence";
    }

    @Override
    public @NotNull String getAuthor() {
        return "M4sh3r";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.startsWith("top_")) {
            return top(params.substring(4));
        }
        if (player == null) {
            return "";
        }
        Team team = plugin.teams().teamOf(player.getUniqueId());
        if (params.equals("has_team")) {
            return team != null ? "yes" : "no";
        }
        if (team == null) {
            return "";
        }
        TeamMember member = team.member(player.getUniqueId());
        return switch (params) {
            case "team_name" -> team.name();
            case "team_tag" -> team.tag();
            case "team_color" -> team.textColor().asHexString();
            case "team_level" -> String.valueOf(team.level());
            case "team_bank" -> Format.money(team.bank());
            case "team_bank_raw" -> String.valueOf(team.bank());
            case "team_members" -> String.valueOf(team.members().size());
            case "team_max_members" -> String.valueOf(plugin.teams().maxMembers(team));
            case "team_online" -> String.valueOf(team.onlineCount());
            case "team_owner" -> plugin.teams().nameOf(team.owner());
            case "rank" -> team.rankOf(player.getUniqueId()).name();
            case "kills" -> member == null ? "0" : String.valueOf(member.kills());
            case "deaths" -> member == null ? "0" : String.valueOf(member.deaths());
            default -> null;
        };
    }

    // %valence_top_1_name% and %valence_top_1_bank%
    private String top(String rest) {
        String[] parts = rest.split("_", 2);
        if (parts.length < 2) {
            return null;
        }
        int index;
        try {
            index = Integer.parseInt(parts[0]) - 1;
        } catch (NumberFormatException e) {
            return null;
        }
        List<Team> top = plugin.teams().topTeams();
        if (index < 0 || index >= top.size()) {
            return "-";
        }
        Team team = top.get(index);
        return switch (parts[1]) {
            case "name" -> team.name();
            case "tag" -> team.tag();
            case "bank" -> Format.money(team.bank());
            case "level" -> String.valueOf(team.level());
            default -> null;
        };
    }
}
