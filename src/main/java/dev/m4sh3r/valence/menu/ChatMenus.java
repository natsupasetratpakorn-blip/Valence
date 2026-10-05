package dev.m4sh3r.valence.menu;

import dev.m4sh3r.valence.Valence;
import dev.m4sh3r.valence.config.Messages;
import dev.m4sh3r.valence.team.Team;
import dev.m4sh3r.valence.team.TeamMember;
import dev.m4sh3r.valence.util.Format;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

import static dev.m4sh3r.valence.config.Messages.c;
import static dev.m4sh3r.valence.config.Messages.p;

/**
 * Used on servers older than 1.21.7, which have no dialog screens. Everything still works through commands.
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
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                line = line.append(msg().parse("<muted>  ·  "));
            }
            line = line.append(parts[i]);
        }
        return line;
    }

    @Override
    public void open(Player player) {
        Team team = plugin.teams().teamOf(player.getUniqueId());
        player.sendMessage(Component.empty());
        if (team == null) {
            player.sendMessage(msg().menu("no-team.text"));
            player.sendMessage(row(
                    suggest("no-team.create", "/team create "),
                    button("no-team.top", "/team top")));
            player.sendMessage(Component.empty());
            return;
        }
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
                suggest("main.invite", "/team invite ")));
        player.sendMessage(row(
                button("main.home", "/team home"),
                button("main.chest", "/team chest"),
                msg().menu("main.chat", c("state", msg().menu(plugin.teams().teamChat(player.getUniqueId()) ? "enabled" : "disabled")))
                        .clickEvent(ClickEvent.runCommand("/team chat")),
                team.owner().equals(player.getUniqueId())
                        ? suggest("main.disband", "/team disband")
                        : suggest("main.leave", "/team leave")));
        player.sendMessage(Component.empty());
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
        player.sendMessage(msg().menu("members.title"));
        for (TeamMember member : team.sortedMembers()) {
            boolean online = Bukkit.getPlayer(member.uuid()) != null;
            player.sendMessage(Component.text("  ")
                    .append(msg().parse(online ? "<good>●" : "<muted>●"))
                    .append(msg().parse(" <main><name></main> <muted>· <rank> · <kills> kills",
                            p("name", member.name()),
                            p("rank", team.rankOf(member.uuid()).name()),
                            p("kills", member.kills()))));
        }
    }

    @Override
    public void openBank(Player player) {
        Team team = plugin.teams().requireTeam(player);
        if (team == null) {
            return;
        }
        String balance = plugin.money().enabled() ? Format.money(plugin.teams().balance(player.getUniqueId())) : "-";
        player.sendMessage(msg().menu("bank.info", p("bank", Format.money(team.bank())), p("balance", balance)));
        player.sendMessage(row(
                suggest("bank.deposit", "/team deposit "),
                suggest("bank.withdraw", "/team withdraw "),
                button("bank.upgrade", "/team upgrade")));
    }

    @Override
    public void openTop(Player player) {
        List<Team> top = plugin.teams().topTeams();
        player.sendMessage(msg().menu("top.title"));
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

    @Override
    public void invitePopup(Player target, Team team, String inviterName) {
        // Chat buttons are already sent with the invite message.
    }
}
