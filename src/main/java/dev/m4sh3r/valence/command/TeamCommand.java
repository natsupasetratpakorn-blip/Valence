package dev.m4sh3r.valence.command;

import dev.m4sh3r.valence.Valence;
import dev.m4sh3r.valence.config.Messages;
import dev.m4sh3r.valence.team.Invite;
import dev.m4sh3r.valence.team.Team;
import dev.m4sh3r.valence.team.TeamManager;
import dev.m4sh3r.valence.team.TeamMember;
import dev.m4sh3r.valence.team.TeamRank;
import dev.m4sh3r.valence.util.Format;
import dev.m4sh3r.valence.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static dev.m4sh3r.valence.config.Messages.c;
import static dev.m4sh3r.valence.config.Messages.p;

public final class TeamCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "invite", "accept", "deny", "join", "leave", "disband", "kick", "promote", "demote",
            "setrank", "transfer", "info", "members", "bank", "deposit", "withdraw", "give", "upgrade", "top",
            "home", "sethome", "chat", "chest", "ff", "help");

    private static final Set<String> MAIN_THREAD = Set.of("deposit", "withdraw", "give", "disband", "home", "sethome", "chest", "enderchest", "ec");

    private final Valence plugin;

    public TeamCommand(Valence plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private TeamManager teams() {
        return plugin.teams();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("valence.admin")) {
                msg().send(sender, "no-permission");
                return true;
            }
            plugin.reload();
            msg().send(sender, "reloaded");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            admin(sender, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            msg().send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("valence.use")) {
            msg().send(player, "no-permission");
            return true;
        }
        if (args.length == 0) {
            plugin.menus().open(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        // Money and teleports stay on the server thread. Everything else is just reading and
        // updating team data, so it runs off the main thread.
        if (MAIN_THREAD.contains(sub)) {
            run(player, sub, args);
        } else {
            Tasks.async(() -> run(player, sub, args));
        }
        return true;
    }

    private void run(Player player, String sub, String[] args) {
        switch (sub) {
            case "create" -> {
                if (args.length < 2) {
                    plugin.menus().openCreate(player);
                } else {
                    teams().create(player, args[1], args.length > 2 ? args[2] : null, null, null);
                }
            }
            case "invite" -> {
                if (args.length < 2) {
                    usage(player);
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null || !player.canSee(target)) {
                    msg().send(player, "player-not-found", p("player", args[1]));
                } else {
                    teams().invite(player, target);
                }
            }
            case "accept" -> teams().accept(player, args.length > 1 ? args[1] : null);
            case "deny" -> teams().deny(player, args.length > 1 ? args[1] : null);
            case "join" -> {
                if (args.length < 2) {
                    usage(player);
                    return;
                }
                Team team = teams().byName(args[1]);
                if (team == null) {
                    msg().send(player, "team-not-found", p("name", args[1]));
                } else {
                    teams().join(player, team);
                }
            }
            case "leave" -> teams().leave(player);
            case "disband" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("confirm")) {
                    teams().disband(player);
                } else if (teams().requireTeam(player) != null) {
                    msg().send(player, "disband.confirm");
                }
            }
            case "kick", "promote", "demote", "transfer" -> {
                if (args.length < 2) {
                    usage(player);
                    return;
                }
                UUID target = member(player, args[1]);
                if (target == null) {
                    return;
                }
                switch (sub) {
                    case "kick" -> teams().kick(player, target);
                    case "promote" -> teams().promote(player, target);
                    case "demote" -> teams().demote(player, target);
                    default -> teams().transfer(player, target);
                }
            }
            case "setrank" -> {
                if (args.length < 3) {
                    usage(player);
                    return;
                }
                UUID target = member(player, args[1]);
                Team team = teams().teamOf(player.getUniqueId());
                if (target == null || team == null) {
                    return;
                }
                String rankName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                TeamRank rank = team.ranks().values().stream()
                        .filter(r -> r.name().equalsIgnoreCase(rankName) || r.id().equalsIgnoreCase(rankName))
                        .findFirst().orElse(null);
                if (rank == null) {
                    msg().send(player, "rank.unknown");
                } else {
                    teams().setRank(player, target, rank.id());
                }
            }
            case "info" -> info(player, args.length > 1 ? teams().byName(args[1]) : teams().teamOf(player.getUniqueId()),
                    args.length > 1 ? args[1] : null);
            case "members" -> plugin.menus().openMembers(player);
            case "bank" -> plugin.menus().openBank(player);
            case "top" -> plugin.menus().openTop(player);
            case "deposit", "withdraw" -> {
                Double amount = args.length > 1 ? Format.parseAmount(args[1]) : null;
                if (amount == null) {
                    msg().send(player, "invalid-amount");
                } else if (sub.equals("deposit")) {
                    teams().deposit(player, amount);
                } else {
                    teams().withdraw(player, amount);
                }
            }
            case "give" -> {
                if (args.length < 3) {
                    usage(player);
                    return;
                }
                OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    target = Bukkit.getOfflinePlayerIfCached(args[1]);
                }
                Double amount = Format.parseAmount(args[2]);
                if (target == null) {
                    msg().send(player, "player-not-found", p("player", args[1]));
                } else if (amount == null) {
                    msg().send(player, "invalid-amount");
                } else {
                    teams().give(player, target, amount);
                }
            }
            case "upgrade" -> teams().upgrade(player);
            case "chest", "enderchest", "ec" -> plugin.chests().open(player);
            case "ff", "friendlyfire", "pvp" -> teams().toggleFriendlyFire(player);
            case "home" -> teams().home(player);
            case "sethome" -> teams().setHome(player);
            case "chat" -> {
                if (args.length > 1) {
                    teams().sendTeamChat(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                } else {
                    teams().toggleTeamChat(player);
                }
            }
            default -> msg().sendList(player, "help");
        }
    }

    private void usage(Player player) {
        msg().sendList(player, "help");
    }

    private UUID member(Player player, String name) {
        Team team = teams().requireTeam(player);
        if (team == null) {
            return null;
        }
        for (TeamMember member : team.members().values()) {
            if (member.name().equalsIgnoreCase(name)) {
                return member.uuid();
            }
        }
        msg().send(player, "target-not-in-your-team", p("player", name));
        return null;
    }

    private void info(Player player, Team team, String searched) {
        if (team == null) {
            if (searched == null) {
                msg().send(player, "not-in-team");
            } else {
                msg().send(player, "team-not-found", p("name", searched));
            }
            return;
        }
        player.sendMessage(msg().menu("team-info.info",
                c("team", team.displayName()),
                p("tag", team.tag()),
                p("owner", teams().nameOf(team.owner())),
                p("level", team.level()),
                p("members", team.members().size()),
                p("max", teams().maxMembers(team)),
                p("bank", Format.money(team.bank()))));
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("valence.admin")) {
            msg().send(sender, "no-permission");
            return;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("disband")) {
            Team team = teams().byName(args[2]);
            if (team == null) {
                msg().send(sender, "team-not-found", p("name", args[2]));
                return;
            }
            teams().adminDisband(team);
            msg().send(sender, "disband.done", c("team", team.displayName()));
            return;
        }
        msg().sendRaw(sender, "<muted>/team admin disband <team>");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(SUBCOMMANDS);
            if (sender.hasPermission("valence.admin")) {
                options.add("reload");
                options.add("admin");
            }
        } else if (args.length == 2 && sender instanceof Player player) {
            Team team = teams().teamOf(player.getUniqueId());
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "invite" -> {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (player.canSee(online) && teams().teamOf(online.getUniqueId()) == null) {
                            options.add(online.getName());
                        }
                    }
                }
                case "kick", "promote", "demote", "transfer", "setrank" -> {
                    if (team != null) {
                        for (TeamMember member : team.members().values()) {
                            if (!member.uuid().equals(player.getUniqueId())) {
                                options.add(member.name());
                            }
                        }
                    }
                }
                case "give" -> {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        options.add(online.getName());
                    }
                }
                case "accept", "deny" -> {
                    for (Invite invite : teams().invitesOf(player.getUniqueId())) {
                        Team invited = teams().team(invite.teamId());
                        if (invited != null) {
                            options.add(invited.name());
                        }
                    }
                }
                case "join" -> teams().openTeams().forEach(open -> options.add(open.name()));
                case "info" -> teams().teams().forEach(any -> options.add(any.name()));
                default -> {
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            options.add("disband");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            teams().teams().forEach(any -> options.add(any.name()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("setrank") && sender instanceof Player player) {
            Team team = teams().teamOf(player.getUniqueId());
            if (team != null) {
                for (TeamRank rank : team.sortedRanks()) {
                    if (!rank.isOwner()) {
                        options.add(rank.name());
                    }
                }
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }
}
