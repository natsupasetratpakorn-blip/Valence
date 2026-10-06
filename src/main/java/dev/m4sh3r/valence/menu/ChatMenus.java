package dev.m4sh3r.valence.menu;

import dev.m4sh3r.valence.Valence;
import dev.m4sh3r.valence.config.Messages;
import dev.m4sh3r.valence.team.LogEntry;
import dev.m4sh3r.valence.team.Team;
import dev.m4sh3r.valence.team.TeamMember;
import dev.m4sh3r.valence.team.TeamPermission;
import dev.m4sh3r.valence.team.TeamRank;
import dev.m4sh3r.valence.util.Format;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

import static dev.m4sh3r.valence.config.Messages.c;
import static dev.m4sh3r.valence.config.Messages.p;

/**
 * Clickable chat pages. Servers older than 1.21.7 have no dialog screens and use these as their menu.
 * The same pages also back commands like /team profile on every version.
 */
public final class ChatMenus implements Menus {

    private final Valence plugin;

    public ChatMenus(Valence plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private Component button(String labelKey, String command) {
        return msg().menu(labelKey)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(command)));
    }

    private Component suggest(String labelKey, String command) {
        return msg().menu(labelKey)
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(command)));
    }

    private Component row(Component... parts) {
        Component line = Component.text("  ");
        boolean first = true;
        for (Component part : parts) {
            if (part == null) {
                continue;
            }
            if (!first) {
                line = line.append(msg().parse("<muted>  ·  "));
            }
            line = line.append(part);
            first = false;
        }
        return line;
    }

    private void header(Player player, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        player.sendMessage(Component.empty());
        player.sendMessage(msg().title(key, resolvers));
    }

    @Override
    public void open(Player player) {
        Team team = plugin.teams().teamOf(player.getUniqueId());
        if (team == null) {
            header(player, "no-team.title");
            player.sendMessage(msg().menu("no-team.text"));
            player.sendMessage(row(
                    suggest("no-team.create", "/team create "),
                    button("no-team.top", "/team top"),
                    suggest("no-team.open-teams", "/team join ")));
            return;
        }
        UUID uuid = player.getUniqueId();
        header(player, "main.title", c("team", team.displayName()));
        player.sendMessage(msg().menu("main.info",
                c("team", team.displayName()),
                p("tag", team.tag()),
                p("level", team.level()),
                p("members", team.members().size()),
                p("max", plugin.teams().maxMembers(team)),
                p("online", team.onlineCount()),
                p("bank", Format.money(team.bank()))));
        player.sendMessage(row(
                button("main.members", "/team members"),
                button("main.bank", "/team bank"),
                button("main.top", "/team top"),
                button("main.players", "/team players")));
        player.sendMessage(row(
                team.has(uuid, TeamPermission.INVITE) ? suggest("main.invite", "/team invite ") : null,
                plugin.teams().homeUnlocked(team) ? button("main.home", "/team home") : null,
                button("main.chest", "/team chest"),
                button("main.waypoint", "/team waypoint"),
                button("main.allies", "/team allies")));
        player.sendMessage(row(
                team.owner().equals(uuid) ? button("main.ranks", "/team ranks") : null,
                team.has(uuid, TeamPermission.EDIT_SETTINGS) ? suggest("main.settings", "/team set ") : null,
                button("main.log", "/team log"),
                msg().menu("main.chat", c("state", msg().menu(plugin.teams().teamChat(uuid) ? "enabled" : "disabled")))
                        .clickEvent(ClickEvent.runCommand("/team chat")),
                msg().menu("main.friendly-fire", c("state", msg().menu(team.member(uuid).friendlyFire() ? "enabled" : "disabled")))
                        .clickEvent(ClickEvent.runCommand("/team ff"))));
        player.sendMessage(row(team.owner().equals(uuid)
                ? suggest("main.disband", "/team disband confirm")
                : suggest("main.leave", "/team leave")));
    }

    @Override
    public void openCreate(Player player) {
        player.sendMessage(msg().menu("create.text"));
        player.sendMessage(row(suggest("create.button", "/team create ")));
    }

    @Override
    public void openMembers(Player player) {
        Team team = plugin.teams().requireTeam(player);
        if (team == null) {
            return;
        }
        header(player, "members.title");
        for (TeamMember member : team.sortedMembers()) {
            boolean online = Bukkit.getPlayer(member.uuid()) != null;
            player.sendMessage(Component.text("  ")
                    .append(msg().parse(online ? "<good>●" : "<muted>●"))
                    .append(msg().parse(" <main><name></main> <muted>· <rank> · <kills> kills",
                            p("name", member.name()),
                            p("rank", team.rankOf(member.uuid()).name()),
                            p("kills", member.kills())))
                    .clickEvent(ClickEvent.runCommand("/team profile " + member.name()))
                    .hoverEvent(HoverEvent.showText(msg().menu("members.text"))));
        }
    }

    public void profile(Player viewer, UUID target) {
        Team team = plugin.teams().requireTeam(viewer);
        TeamMember member = team == null ? null : team.member(target);
        if (member == null) {
            return;
        }
        boolean online = Bukkit.getPlayer(target) != null;
        String balance = plugin.money().enabled() ? Format.money(plugin.teams().balance(target)) : "-";
        header(viewer, "profile.title", p("player", member.name()));
        viewer.sendMessage(msg().menu("profile.info",
                p("player", member.name()),
                c("rank", plugin.teams().rankName(team.rankOf(target))),
                c("status", msg().menu(online ? "online" : "offline"))));
        viewer.sendMessage(msg().menu("profile.stats",
                p("balance", balance),
                p("playtime", Format.duration(plugin.teams().playtimeSeconds(target))),
                p("joined", Format.date(member.joinedAt())),
                p("kills", member.kills()),
                p("deaths", member.deaths()),
                p("deposited", Format.money(member.deposited()))));
        UUID uuid = viewer.getUniqueId();
        if (uuid.equals(target) || !plugin.teams().outranks(team, uuid, target)) {
            return;
        }
        String name = member.name();
        viewer.sendMessage(row(
                team.has(uuid, TeamPermission.MANAGE_MEMBERS) ? button("profile.promote", "/team promote " + name) : null,
                team.has(uuid, TeamPermission.MANAGE_MEMBERS) ? button("profile.demote", "/team demote " + name) : null,
                team.has(uuid, TeamPermission.MANAGE_MEMBERS) ? suggest("profile.set-rank", "/team setrank " + name + " ") : null,
                team.has(uuid, TeamPermission.KICK) ? suggest("profile.kick", "/team kick " + name) : null,
                team.owner().equals(uuid) ? suggest("profile.transfer", "/team transfer " + name) : null));
    }

    @Override
    public void openBank(Player player) {
        Team team = plugin.teams().requireTeam(player);
        if (team == null) {
            return;
        }
        String balance = plugin.money().enabled() ? Format.money(plugin.teams().balance(player.getUniqueId())) : "-";
        header(player, "bank.title");
        player.sendMessage(msg().menu("bank.info", p("bank", Format.money(team.bank())), p("balance", balance)));
        player.sendMessage(row(
                suggest("bank.deposit", "/team deposit "),
                team.owner().equals(player.getUniqueId()) ? suggest("bank.withdraw", "/team withdraw ") : null,
                team.owner().equals(player.getUniqueId()) ? suggest("bank.give", "/team give ") : null,
                button("bank.upgrade", "/team upgrade")));
    }

    @Override
    public void openTop(Player player) {
        List<Team> top = plugin.teams().topTeams();
        header(player, "top.title");
        if (top.isEmpty()) {
            player.sendMessage(msg().menu("top.empty"));
            return;
        }
        int position = 1;
        for (Team team : top) {
            player.sendMessage(Component.text("  ").append(msg().menu("top.entry",
                    p("position", position++),
                    c("team", team.displayName()),
                    p("bank", Format.money(team.bank())),
                    p("level", team.level()))));
        }
    }

    public void players(Player player) {
        Team team = plugin.teams().requireTeam(player);
        if (team == null) {
            return;
        }
        header(player, "players.title");
        List<TeamMember> members = new ArrayList<>(team.members().values());
        ranking(player, "players.kills", members, TeamMember::kills, value -> String.valueOf((long) value));
        if (plugin.money().enabled()) {
            ranking(player, "players.money", members, m -> plugin.teams().balance(m.uuid()), Format::money);
        }
        ranking(player, "players.deposits", members, TeamMember::deposited, Format::money);
    }

    private void ranking(Player player, String key, List<TeamMember> members, ToDoubleFunction<TeamMember> value,
                         java.util.function.DoubleFunction<String> format) {
        List<TeamMember> sorted = new ArrayList<>(members);
        sorted.sort(Comparator.comparingDouble(value).reversed());
        player.sendMessage(msg().menu(key));
        int position = 1;
        for (TeamMember member : sorted.subList(0, Math.min(5, sorted.size()))) {
            player.sendMessage(Component.text("  ").append(msg().menu("players.entry",
                    p("position", position++), p("player", member.name()),
                    p("value", format.apply(value.applyAsDouble(member))))));
        }
    }

    public void log(Player player) {
        Team team = plugin.teams().requireTeam(player);
        if (team == null) {
            return;
        }
        header(player, "log.title");
        int shown = 0;
        for (LogEntry entry : team.log()) {
            if (shown++ == 15) {
                break;
            }
            player.sendMessage(Component.text("  ").append(msg().menu("log.entry",
                    p("time", Format.ago(entry.time())), p("text", entry.text()))));
        }
        if (shown == 0) {
            player.sendMessage(msg().menu("log.empty"));
        }
    }

    public void ranks(Player player) {
        Team team = plugin.teams().requireTeam(player);
        if (team == null) {
            return;
        }
        header(player, "ranks.title");
        for (TeamRank rank : team.sortedRanks()) {
            List<String> perms = new ArrayList<>();
            for (TeamPermission permission : rank.permissions()) {
                perms.add(permission.key());
            }
            player.sendMessage(Component.text("  ").append(msg().parse(
                    "<hl><name></hl> <muted>· position <weight> · <perms>",
                    p("name", rank.name()), p("weight", rank.weight()),
                    p("perms", rank.isOwner() ? "everything" : perms.isEmpty() ? "no permissions" : String.join(", ", perms)))));
        }
        if (team.owner().equals(player.getUniqueId())) {
            player.sendMessage(row(
                    suggest("ranks.new", "/team rank create "),
                    msg().parse("<muted><nosc>/team rank perm <rank> <permission> on|off</nosc>")
                            .clickEvent(ClickEvent.suggestCommand("/team rank perm "))));
        }
    }

    public void allies(Player player) {
        Team team = plugin.teams().requireTeam(player);
        if (team == null) {
            return;
        }
        header(player, "allies.title");
        List<Team> allies = plugin.teams().allies(team);
        if (allies.isEmpty()) {
            player.sendMessage(msg().menu("allies.empty"));
        }
        boolean manage = team.has(player.getUniqueId(), TeamPermission.MANAGE_ALLIES);
        for (Team ally : allies) {
            Component line = Component.text("  ").append(ally.displayName()).append(msg().parse(" <muted>[<tag>]", p("tag", ally.tag())));
            if (manage) {
                line = line.append(Component.text("  ")).append(suggest("allies.remove", "/team ally remove " + ally.name()));
            }
            player.sendMessage(line);
        }
        if (!manage) {
            return;
        }
        for (Team from : plugin.teams().allyRequestsFor(team)) {
            player.sendMessage(Component.text("  ").append(msg().menu("allies.request", c("team", from.displayName())))
                    .append(Component.text("  ")).append(button("allies.accept", "/team ally accept " + from.name())));
        }
        player.sendMessage(row(suggest("allies.add", "/team ally ")));
    }

    @Override
    public void invitePopup(Player target, Team team, String inviterName) {
        // Chat buttons are already sent with the invite message.
    }
}
