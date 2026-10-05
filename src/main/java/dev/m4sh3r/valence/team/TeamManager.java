package dev.m4sh3r.valence.team;

import dev.m4sh3r.valence.Valence;
import dev.m4sh3r.valence.config.Messages;
import dev.m4sh3r.valence.config.Settings;
import dev.m4sh3r.valence.economy.Money;
import dev.m4sh3r.valence.storage.TeamStorage;
import dev.m4sh3r.valence.util.Format;
import dev.m4sh3r.valence.util.Heads;
import dev.m4sh3r.valence.util.Tasks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static dev.m4sh3r.valence.config.Messages.c;
import static dev.m4sh3r.valence.config.Messages.p;

public final class TeamManager {

    public static final int MAX_RANKS = 10;
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]+");

    private final Valence plugin;
    private final Map<UUID, Team> teams = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerTeams = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Invite>> invites = new ConcurrentHashMap<>();
    private final Set<UUID> teamChat = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> homeCooldowns = new ConcurrentHashMap<>();

    public TeamManager(Valence plugin) {
        this.plugin = plugin;
    }

    private Settings settings() {
        return plugin.settings();
    }

    private Messages msg() {
        return plugin.messages();
    }

    private Money money() {
        return plugin.money();
    }

    public synchronized void load(Collection<Team> loaded) {
        teams.clear();
        playerTeams.clear();
        for (Team team : loaded) {
            team.color(team.color(), settings().color(team.color()));
            teams.put(team.id(), team);
            for (UUID member : team.members().keySet()) {
                playerTeams.put(member, team.id());
            }
        }
    }

    public synchronized void refreshColors() {
        for (Team team : teams.values()) {
            team.color(team.color(), settings().color(team.color()));
        }
    }

    // Lookups

    public Collection<Team> teams() {
        return teams.values();
    }

    public Team team(UUID teamId) {
        return teamId == null ? null : teams.get(teamId);
    }

    public Team teamOf(UUID player) {
        return team(playerTeams.get(player));
    }

    public Team byName(String name) {
        if (name == null) {
            return null;
        }
        for (Team team : teams.values()) {
            if (team.name().equalsIgnoreCase(name)) {
                return team;
            }
        }
        return null;
    }

    public int maxMembers(Team team) {
        return settings().level(team.level()).maxMembers();
    }

    public boolean full(Team team) {
        return team.members().size() >= maxMembers(team);
    }

    public List<Team> topTeams() {
        List<Team> list = new ArrayList<>(teams.values());
        list.sort(Comparator.comparingDouble((Team t) -> t.bank()).reversed().thenComparing(t -> t.name()));
        return list.subList(0, Math.min(settings().leaderboardSize, list.size()));
    }

    public List<Team> openTeams() {
        List<Team> list = new ArrayList<>();
        for (Team team : teams.values()) {
            if (team.open() && !full(team)) {
                list.add(team);
            }
        }
        list.sort(Comparator.comparingInt((Team t) -> t.members().size()).reversed().thenComparing(t -> t.name()));
        return list;
    }

    // Balances and playtime can mean a database call or a file read, so they are cached and only
    // looked up from menu code, which runs off the main thread.
    private static final long CACHE_MILLIS = 30_000;
    private final Map<UUID, long[]> playtimeCache = new ConcurrentHashMap<>();
    private final Map<UUID, double[]> balanceCache = new ConcurrentHashMap<>();

    public long playtimeSeconds(UUID uuid) {
        long now = System.currentTimeMillis();
        long[] cached = playtimeCache.get(uuid);
        if (cached != null && now - cached[1] < CACHE_MILLIS) {
            return cached[0];
        }
        long value;
        try {
            value = Bukkit.getOfflinePlayer(uuid).getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L;
        } catch (Exception e) {
            value = 0;
        }
        playtimeCache.put(uuid, new long[]{value, now});
        return value;
    }

    public double balance(UUID uuid) {
        if (!money().enabled()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        double[] cached = balanceCache.get(uuid);
        if (cached != null && now - (long) cached[1] < CACHE_MILLIS) {
            return cached[0];
        }
        double value = money().balance(Bukkit.getOfflinePlayer(uuid));
        balanceCache.put(uuid, new double[]{value, now});
        return value;
    }

    public void forgetBalance(UUID uuid) {
        balanceCache.remove(uuid);
    }

    public boolean teamChat(UUID uuid) {
        return teamChat.contains(uuid);
    }

    // Creating and joining

    public synchronized Team create(Player player, String name, String tag, String colorName, Material icon) {
        if (!player.hasPermission("valence.create")) {
            msg().send(player, "no-permission");
            return null;
        }
        if (teamOf(player.getUniqueId()) != null) {
            msg().send(player, "already-in-team");
            return null;
        }
        name = name == null ? "" : name.trim();
        if (tag == null || tag.isBlank()) {
            tag = name.substring(0, Math.min(name.length(), settings().tagMax)).toUpperCase(Locale.ROOT);
        }
        tag = tag.trim();
        if (!validName(player, name, null) || !validTag(player, tag)) {
            return null;
        }
        String color = settings().colorName(colorName == null ? "" : colorName);
        if (color == null) {
            color = settings().defaultColor;
        }
        if (icon == null) {
            icon = settings().defaultIcon;
        }

        Team team = new Team(UUID.randomUUID(), name, tag, color, icon, player.getUniqueId(), System.currentTimeMillis());
        team.color(color, settings().color(color));
        team.friendlyFire(settings().friendlyFire);
        TeamStorage.ensureBuiltInRanks(team);
        TeamMember owner = new TeamMember(player.getUniqueId(), player.getName(), TeamRank.OWNER, System.currentTimeMillis());
        applySkin(owner, player);
        team.members().put(owner.uuid(), owner);
        teams.put(team.id(), team);
        playerTeams.put(player.getUniqueId(), team.id());
        invites.remove(player.getUniqueId());
        team.log(player.getName() + " created the team", settings().logSize);
        msg().send(player, "create.success", c("team", team.displayName()));
        return team;
    }

    private boolean validName(Player player, String name, Team ignore) {
        if (name.length() < settings().nameMin || name.length() > settings().nameMax) {
            msg().send(player, "create.name-length", p("min", settings().nameMin), p("max", settings().nameMax));
            return false;
        }
        if (!VALID_NAME.matcher(name).matches()) {
            msg().send(player, "create.name-invalid");
            return false;
        }
        if (settings().blockedNames.contains(name.toLowerCase(Locale.ROOT))) {
            msg().send(player, "create.name-blocked");
            return false;
        }
        Team existing = byName(name);
        if (existing != null && existing != ignore) {
            msg().send(player, "create.name-taken", p("name", name));
            return false;
        }
        return true;
    }

    private boolean validTag(Player player, String tag) {
        if (tag.length() < settings().tagMin || tag.length() > settings().tagMax) {
            msg().send(player, "create.tag-length", p("min", settings().tagMin), p("max", settings().tagMax));
            return false;
        }
        if (!VALID_NAME.matcher(tag).matches()) {
            msg().send(player, "create.name-invalid");
            return false;
        }
        return true;
    }

    public List<Invite> invitesOf(UUID player) {
        Map<UUID, Invite> map = invites.get(player);
        List<Invite> list = new ArrayList<>();
        if (map == null) {
            return list;
        }
        map.values().removeIf(invite -> invite.expired() || !teams.containsKey(invite.teamId()));
        list.addAll(map.values());
        list.sort(Comparator.comparingLong(Invite::expiresAt));
        return list;
    }

    public synchronized boolean invite(Player inviter, Player target) {
        Team team = requireTeam(inviter);
        if (team == null || !require(inviter, team, TeamPermission.INVITE)) {
            return false;
        }
        if (target.getUniqueId().equals(inviter.getUniqueId())) {
            msg().send(inviter, "invite.self");
            return false;
        }
        if (teamOf(target.getUniqueId()) != null) {
            msg().send(inviter, "target-in-team", p("player", target.getName()));
            return false;
        }
        if (full(team)) {
            msg().send(inviter, "invite.team-full");
            return false;
        }
        Map<UUID, Invite> map = invites.computeIfAbsent(target.getUniqueId(), k -> new ConcurrentHashMap<>());
        Invite existing = map.get(team.id());
        if (existing != null && !existing.expired()) {
            msg().send(inviter, "invite.already-invited", p("player", target.getName()));
            return false;
        }
        long expires = System.currentTimeMillis() + settings().inviteExpireSeconds * 1000L;
        Invite invite = new Invite(team.id(), inviter.getUniqueId(), inviter.getName(), expires);
        map.put(team.id(), invite);

        msg().send(inviter, "invite.sent", p("player", target.getName()));
        msg().send(target, "invite.received", p("player", inviter.getName()), c("team", team.displayName()));
        target.sendMessage(inviteButtons(team));
        plugin.menus().invitePopup(target, team, inviter.getName());

        UUID targetId = target.getUniqueId();
        Tasks.asyncLater(() -> {
            Map<UUID, Invite> current = invites.get(targetId);
            if (current != null && current.remove(team.id(), invite)) {
                Player online = Bukkit.getPlayer(targetId);
                if (online != null) {
                    msg().send(online, "invite.expired", c("team", team.displayName()));
                }
            }
        }, settings().inviteExpireSeconds, TimeUnit.SECONDS);
        return true;
    }

    private Component inviteButtons(Team team) {
        String name = team.name();
        TagResolver accept = TagResolver.resolver("accept", Tag.styling(ClickEvent.runCommand("/team accept " + name)));
        TagResolver deny = TagResolver.resolver("deny", Tag.styling(ClickEvent.runCommand("/team deny " + name)));
        return msg().chat("invite.buttons", accept, deny);
    }

    private Invite findInvite(Player player, String teamName) {
        List<Invite> list = invitesOf(player.getUniqueId());
        if (teamName == null || teamName.isBlank()) {
            return list.size() == 1 ? list.get(0) : null;
        }
        Team team = byName(teamName);
        if (team == null) {
            return null;
        }
        for (Invite invite : list) {
            if (invite.teamId().equals(team.id())) {
                return invite;
            }
        }
        return null;
    }

    public synchronized boolean accept(Player player, String teamName) {
        if (teamOf(player.getUniqueId()) != null) {
            msg().send(player, "already-in-team");
            return false;
        }
        Invite invite = findInvite(player, teamName);
        Team team = invite == null ? null : team(invite.teamId());
        if (team == null) {
            msg().send(player, "invite.none");
            return false;
        }
        if (full(team)) {
            msg().send(player, "invite.team-full");
            return false;
        }
        addMember(team, player);
        return true;
    }

    public synchronized boolean deny(Player player, String teamName) {
        Invite invite = findInvite(player, teamName);
        Team team = invite == null ? null : team(invite.teamId());
        if (team == null) {
            msg().send(player, "invite.none");
            return false;
        }
        Map<UUID, Invite> map = invites.get(player.getUniqueId());
        if (map != null) {
            map.remove(team.id());
        }
        msg().send(player, "invite.denied", c("team", team.displayName()));
        Player inviter = Bukkit.getPlayer(invite.inviter());
        if (inviter != null) {
            msg().send(inviter, "invite.denied-notify", p("player", player.getName()));
        }
        return true;
    }

    public synchronized boolean join(Player player, Team team) {
        if (teamOf(player.getUniqueId()) != null) {
            msg().send(player, "already-in-team");
            return false;
        }
        boolean invited = invitesOf(player.getUniqueId()).stream().anyMatch(i -> i.teamId().equals(team.id()));
        if (!team.open() && !invited) {
            msg().send(player, "join.closed");
            return false;
        }
        if (full(team)) {
            msg().send(player, "invite.team-full");
            return false;
        }
        addMember(team, player);
        return true;
    }

    private void addMember(Team team, Player player) {
        TeamMember member = new TeamMember(player.getUniqueId(), player.getName(), TeamRank.MEMBER, System.currentTimeMillis());
        applySkin(member, player);
        team.members().put(member.uuid(), member);
        playerTeams.put(player.getUniqueId(), team.id());
        invites.remove(player.getUniqueId());
        team.log(player.getName() + " joined", settings().logSize);
        broadcast(team, msg().chat("join.broadcast", p("player", player.getName())), player.getUniqueId());
        msg().send(player, "join.joined", c("team", team.displayName()));
    }

    // Leaving

    public synchronized boolean leave(Player player) {
        Team team = requireTeam(player);
        if (team == null) {
            return false;
        }
        if (team.owner().equals(player.getUniqueId())) {
            msg().send(player, "leave.owner");
            return false;
        }
        removeMember(team, player.getUniqueId());
        team.log(player.getName() + " left", settings().logSize);
        msg().send(player, "leave.left", c("team", team.displayName()));
        broadcast(team, msg().chat("leave.broadcast", p("player", player.getName())), null);
        return true;
    }

    public synchronized boolean disband(Player player) {
        Team team = requireTeam(player);
        if (team == null || !requireOwner(player, team)) {
            return false;
        }
        if (team.bank() > 0 && money().enabled()) {
            money().give(player, team.bank());
        }
        broadcast(team, msg().chat("disband.broadcast"), player.getUniqueId());
        msg().send(player, "disband.done", c("team", team.displayName()));
        delete(team);
        return true;
    }

    public synchronized void adminDisband(Team team) {
        broadcast(team, msg().chat("disband.broadcast"), null);
        delete(team);
    }

    private void delete(Team team) {
        teams.remove(team.id());
        for (UUID member : team.members().keySet()) {
            playerTeams.remove(member, team.id());
            teamChat.remove(member);
        }
        for (Map<UUID, Invite> map : invites.values()) {
            map.remove(team.id());
        }
        Tasks.async(() -> plugin.storage().delete(team.id()));
    }

    private void removeMember(Team team, UUID uuid) {
        team.members().remove(uuid);
        playerTeams.remove(uuid, team.id());
        teamChat.remove(uuid);
        team.markDirty();
    }

    public synchronized boolean kick(Player actor, UUID target) {
        Team team = requireTeam(actor);
        if (team == null || !require(actor, team, TeamPermission.KICK)) {
            return false;
        }
        TeamMember member = team.member(target);
        if (member == null) {
            msg().send(actor, "target-not-in-your-team", p("player", nameOf(target)));
            return false;
        }
        if (target.equals(actor.getUniqueId())) {
            msg().send(actor, "kick.self");
            return false;
        }
        if (!outranks(team, actor.getUniqueId(), target)) {
            msg().send(actor, "cant-manage");
            return false;
        }
        removeMember(team, target);
        team.log(actor.getName() + " kicked " + member.name(), settings().logSize);
        msg().send(actor, "kick.done", p("player", member.name()));
        broadcast(team, msg().chat("kick.broadcast", p("player", member.name())), actor.getUniqueId());
        Player kicked = Bukkit.getPlayer(target);
        if (kicked != null) {
            msg().send(kicked, "kick.target", c("team", team.displayName()));
        }
        return true;
    }

    // Ranks

    public boolean outranks(Team team, UUID actor, UUID target) {
        return team.weightOf(actor) > team.weightOf(target);
    }

    public synchronized boolean promote(Player actor, UUID target) {
        return shift(actor, target, true);
    }

    public synchronized boolean demote(Player actor, UUID target) {
        return shift(actor, target, false);
    }

    private boolean shift(Player actor, UUID target, boolean up) {
        Team team = requireTeam(actor);
        if (team == null || !require(actor, team, TeamPermission.MANAGE_MEMBERS)) {
            return false;
        }
        TeamMember member = team.member(target);
        if (member == null) {
            msg().send(actor, "target-not-in-your-team", p("player", nameOf(target)));
            return false;
        }
        if (!outranks(team, actor.getUniqueId(), target)) {
            msg().send(actor, "cant-manage");
            return false;
        }
        int current = team.weightOf(target);
        int actorWeight = team.weightOf(actor.getUniqueId());
        TeamRank next = null;
        for (TeamRank rank : team.ranks().values()) {
            if (rank.isOwner()) {
                continue;
            }
            if (up && rank.weight() > current && rank.weight() < actorWeight
                    && (next == null || rank.weight() < next.weight())) {
                next = rank;
            }
            if (!up && rank.weight() < current && (next == null || rank.weight() > next.weight())) {
                next = rank;
            }
        }
        if (next == null) {
            msg().send(actor, up ? "rank.highest" : "rank.lowest", p("player", member.name()));
            return false;
        }
        member.rankId(next.id());
        team.log(actor.getName() + (up ? " promoted " : " demoted ") + member.name() + " to " + next.name(), settings().logSize);
        Component rank = rankName(next);
        msg().send(actor, up ? "rank.promoted" : "rank.demoted", p("player", member.name()), c("rank", rank));
        notifyOnline(target, msg().chat("rank.you", c("rank", rank)));
        return true;
    }

    public synchronized boolean setRank(Player actor, UUID target, String rankId) {
        Team team = requireTeam(actor);
        if (team == null || !require(actor, team, TeamPermission.MANAGE_MEMBERS)) {
            return false;
        }
        TeamMember member = team.member(target);
        TeamRank rank = team.rank(rankId);
        if (member == null) {
            msg().send(actor, "target-not-in-your-team", p("player", nameOf(target)));
            return false;
        }
        if (rank == null || rank.isOwner()) {
            msg().send(actor, "rank.unknown");
            return false;
        }
        if (!outranks(team, actor.getUniqueId(), target) || rank.weight() >= team.weightOf(actor.getUniqueId())) {
            msg().send(actor, "cant-manage");
            return false;
        }
        member.rankId(rank.id());
        team.log(actor.getName() + " set " + member.name() + " to " + rank.name(), settings().logSize);
        msg().send(actor, "rank.set", p("player", member.name()), c("rank", rankName(rank)));
        notifyOnline(target, msg().chat("rank.you", c("rank", rankName(rank))));
        return true;
    }

    public synchronized boolean transfer(Player actor, UUID target) {
        Team team = requireTeam(actor);
        if (team == null || !requireOwner(actor, team)) {
            return false;
        }
        TeamMember member = team.member(target);
        if (member == null || target.equals(actor.getUniqueId())) {
            msg().send(actor, "target-not-in-your-team", p("player", nameOf(target)));
            return false;
        }
        TeamRank highest = null;
        for (TeamRank rank : team.ranks().values()) {
            if (!rank.isOwner() && (highest == null || rank.weight() > highest.weight())) {
                highest = rank;
            }
        }
        TeamMember old = team.member(actor.getUniqueId());
        old.rankId(highest == null ? TeamRank.MEMBER : highest.id());
        member.rankId(TeamRank.OWNER);
        team.owner(target);
        team.log(actor.getName() + " gave ownership to " + member.name(), settings().logSize);
        msg().send(actor, "transfer.done", p("player", member.name()));
        notifyOnline(target, msg().chat("transfer.target", c("team", team.displayName())));
        return true;
    }

    public synchronized TeamRank createRank(Player actor, String name, Material icon, int weight, Set<TeamPermission> permissions) {
        Team team = requireTeam(actor);
        if (team == null || !requireOwner(actor, team)) {
            return null;
        }
        if (team.ranks().size() >= MAX_RANKS) {
            msg().send(actor, "rank.limit", p("max", MAX_RANKS));
            return null;
        }
        name = name == null ? "" : name.trim();
        if (name.isEmpty() || name.length() > 16) {
            msg().send(actor, "rank.name-invalid");
            return null;
        }
        String id;
        do {
            id = "r" + UUID.randomUUID().toString().substring(0, 6);
        } while (team.ranks().containsKey(id));
        TeamRank rank = new TeamRank(id, name, icon == null ? Material.PAPER : icon, clampWeight(weight));
        rank.permissions().addAll(permissions);
        team.ranks().put(id, rank);
        team.log(actor.getName() + " created the rank " + name, settings().logSize);
        msg().send(actor, "rank.created", c("rank", rankName(rank)));
        return rank;
    }

    public synchronized boolean updateRank(Player actor, String rankId, String name, Material icon, int weight, Set<TeamPermission> permissions) {
        Team team = requireTeam(actor);
        if (team == null || !requireOwner(actor, team)) {
            return false;
        }
        TeamRank rank = team.rank(rankId);
        if (rank == null) {
            msg().send(actor, "rank.unknown");
            return false;
        }
        name = name == null ? "" : name.trim();
        if (name.isEmpty() || name.length() > 16) {
            msg().send(actor, "rank.name-invalid");
            return false;
        }
        rank.name(name);
        if (icon != null) {
            rank.icon(icon);
        }
        if (!rank.builtIn()) {
            rank.weight(clampWeight(weight));
        }
        if (!rank.isOwner()) {
            rank.permissions().clear();
            rank.permissions().addAll(permissions);
        }
        team.markDirty();
        msg().send(actor, "rank.saved", c("rank", rankName(rank)));
        return true;
    }

    public synchronized boolean deleteRank(Player actor, String rankId) {
        Team team = requireTeam(actor);
        if (team == null || !requireOwner(actor, team)) {
            return false;
        }
        TeamRank rank = team.rank(rankId);
        if (rank == null || rank.builtIn()) {
            msg().send(actor, "rank.unknown");
            return false;
        }
        team.ranks().remove(rankId);
        for (TeamMember member : team.members().values()) {
            if (member.rankId().equals(rankId)) {
                member.rankId(TeamRank.MEMBER);
            }
        }
        team.log(actor.getName() + " deleted the rank " + rank.name(), settings().logSize);
        msg().send(actor, "rank.deleted", c("rank", rankName(rank)), p("default", team.rank(TeamRank.MEMBER).name()));
        return true;
    }

    private int clampWeight(int weight) {
        return Math.max(1, Math.min(99, weight));
    }

    public Component rankName(TeamRank rank) {
        return msg().parse("<hl><name></hl>", p("name", rank.name()));
    }

    // Bank

    public synchronized boolean deposit(Player actor, double amount) {
        Team team = requireTeam(actor);
        if (team == null || !requireEconomy(actor) || !require(actor, team, TeamPermission.DEPOSIT)) {
            return false;
        }
        if (amount < settings().minDeposit) {
            msg().send(actor, "bank.min-deposit", p("amount", Format.money(settings().minDeposit)));
            return false;
        }
        if (!money().take(actor, amount)) {
            msg().send(actor, "bank.not-enough");
            return false;
        }
        team.bank(team.bank() + amount);
        forgetBalance(actor.getUniqueId());
        TeamMember member = team.member(actor.getUniqueId());
        member.deposited(member.deposited() + amount);
        team.log(actor.getName() + " deposited " + Format.money(amount), settings().logSize);
        msg().send(actor, "bank.deposited", p("amount", Format.money(amount)));
        broadcast(team, msg().chat("bank.deposit-broadcast", p("player", actor.getName()), p("amount", Format.money(amount))), actor.getUniqueId());
        return true;
    }

    public synchronized boolean withdraw(Player actor, double amount) {
        Team team = requireTeam(actor);
        if (team == null || !requireEconomy(actor) || !requireOwner(actor, team)) {
            return false;
        }
        if (team.bank() < amount) {
            msg().send(actor, "bank.bank-not-enough");
            return false;
        }
        if (!money().give(actor, amount)) {
            msg().send(actor, "no-economy");
            return false;
        }
        team.bank(team.bank() - amount);
        forgetBalance(actor.getUniqueId());
        team.log(actor.getName() + " withdrew " + Format.money(amount), settings().logSize);
        msg().send(actor, "bank.withdrew", p("amount", Format.money(amount)));
        return true;
    }

    public synchronized boolean give(Player actor, OfflinePlayer target, double amount) {
        Team team = requireTeam(actor);
        if (team == null || !requireEconomy(actor) || !requireOwner(actor, team)) {
            return false;
        }
        if (team.bank() < amount) {
            msg().send(actor, "bank.bank-not-enough");
            return false;
        }
        if (!money().give(target, amount)) {
            msg().send(actor, "no-economy");
            return false;
        }
        String name = target.getName() == null ? "Unknown" : target.getName();
        team.bank(team.bank() - amount);
        forgetBalance(target.getUniqueId());
        team.log(actor.getName() + " gave " + Format.money(amount) + " to " + name, settings().logSize);
        msg().send(actor, "bank.gave", p("amount", Format.money(amount)), p("player", name));
        if (target.getPlayer() != null && !target.getUniqueId().equals(actor.getUniqueId())) {
            msg().send(target.getPlayer(), "bank.received", p("amount", Format.money(amount)));
        }
        return true;
    }

    public synchronized boolean upgrade(Player actor) {
        Team team = requireTeam(actor);
        if (team == null || !require(actor, team, TeamPermission.UPGRADE)) {
            return false;
        }
        Settings.Level next = settings().nextLevel(team.level());
        if (next == null) {
            msg().send(actor, "level.max");
            return false;
        }
        if (team.bank() < next.cost()) {
            msg().send(actor, "level.cost", p("amount", Format.money(next.cost())));
            return false;
        }
        team.bank(team.bank() - next.cost());
        team.level(next.level());
        team.log(actor.getName() + " leveled the team to " + next.level(), settings().logSize);
        broadcast(team, msg().chat("level.upgraded", p("level", next.level()), p("members", next.maxMembers())), null);
        return true;
    }

    // Settings and home

    public synchronized boolean updateSettings(Player actor, String name, String tag, String color, Material icon,
                                               boolean open, boolean friendlyFire) {
        Team team = requireTeam(actor);
        if (team == null || !require(actor, team, TeamPermission.EDIT_SETTINGS)) {
            return false;
        }
        name = name == null ? team.name() : name.trim();
        tag = tag == null ? team.tag() : tag.trim();
        if (!name.equals(team.name()) && !validName(actor, name, team)) {
            return false;
        }
        if (!tag.equals(team.tag()) && !validTag(actor, tag)) {
            return false;
        }
        String colorName = settings().colorName(color == null ? "" : color);
        team.name(name);
        team.tag(tag);
        if (colorName != null) {
            team.color(colorName, settings().color(colorName));
        }
        if (icon != null) {
            team.icon(icon);
        }
        team.open(open);
        team.friendlyFire(friendlyFire);
        team.log(actor.getName() + " changed the team settings", settings().logSize);
        msg().send(actor, "settings.saved");
        return true;
    }

    public boolean homeUnlocked(Team team) {
        return settings().homeEnabled && team.level() >= settings().homeUnlockLevel;
    }

    public synchronized boolean setHome(Player actor) {
        Team team = requireTeam(actor);
        if (team == null || !require(actor, team, TeamPermission.SET_HOME) || !checkHomeAvailable(actor, team)) {
            return false;
        }
        team.home(TeamHome.of(actor.getLocation()));
        team.log(actor.getName() + " set the team home", settings().logSize);
        msg().send(actor, "home.set");
        return true;
    }

    private boolean checkHomeAvailable(Player actor, Team team) {
        if (!settings().homeEnabled) {
            msg().send(actor, "home.disabled");
            return false;
        }
        if (team.level() < settings().homeUnlockLevel) {
            msg().send(actor, "home.locked", p("level", settings().homeUnlockLevel));
            return false;
        }
        return true;
    }

    public boolean home(Player actor) {
        Team team = requireTeam(actor);
        if (team == null || !require(actor, team, TeamPermission.USE_HOME) || !checkHomeAvailable(actor, team)) {
            return false;
        }
        TeamHome home = team.home();
        Location target = home == null ? null : home.toLocation();
        if (target == null) {
            msg().send(actor, "home.none");
            return false;
        }
        long now = System.currentTimeMillis();
        Long until = homeCooldowns.get(actor.getUniqueId());
        if (until != null && until > now && !actor.hasPermission("valence.admin")) {
            msg().send(actor, "home.cooldown", p("seconds", (until - now + 999) / 1000));
            return false;
        }
        int warmup = actor.hasPermission("valence.admin") ? 0 : settings().homeWarmup;
        if (warmup <= 0) {
            teleportHome(actor, target);
            return true;
        }
        Location start = actor.getLocation();
        msg().send(actor, "home.warmup", p("seconds", warmup));
        Tasks.entityLater(actor, () -> {
            Location now2 = actor.getLocation();
            if (!now2.getWorld().equals(start.getWorld()) || now2.distanceSquared(start) > 0.6) {
                msg().send(actor, "home.moved");
                return;
            }
            teleportHome(actor, target);
        }, warmup * 20L);
        return true;
    }

    private void teleportHome(Player player, Location target) {
        homeCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + settings().homeCooldown * 1000L);
        player.teleportAsync(target).thenAccept(success -> {
            if (success) {
                msg().send(player, "home.teleported");
            }
        });
    }

    // Chat

    public void toggleTeamChat(Player player) {
        if (requireTeam(player) == null) {
            return;
        }
        if (teamChat.remove(player.getUniqueId())) {
            msg().send(player, "team-chat.disabled");
        } else {
            teamChat.add(player.getUniqueId());
            msg().send(player, "team-chat.enabled");
        }
    }

    public void sendTeamChat(Player player, String message) {
        Team team = requireTeam(player);
        if (team == null) {
            return;
        }
        Component line = msg().parse(settings().teamChatFormat,
                Placeholder.styling("team_color", team.textColor()),
                p("team_tag", team.tag()),
                p("team", team.name()),
                p("player", player.getName()),
                p("rank", team.rankOf(player.getUniqueId()).name()),
                p("message", message));
        broadcast(team, line, null);
        Bukkit.getConsoleSender().sendMessage(line);
    }

    public void messageMember(Player actor, UUID target, String message) {
        Team team = requireTeam(actor);
        if (team == null || message == null || message.isBlank()) {
            return;
        }
        TeamMember member = team.member(target);
        Player online = Bukkit.getPlayer(target);
        if (member == null || online == null) {
            msg().send(actor, "message.offline", p("player", member == null ? nameOf(target) : member.name()));
            return;
        }
        msg().send(actor, "message.sent", p("player", member.name()), p("message", message.trim()));
        msg().send(online, "message.received", p("player", actor.getName()), p("message", message.trim()));
    }

    // Stats and presence

    public void recordKill(UUID killer) {
        Team team = teamOf(killer);
        TeamMember member = team == null ? null : team.member(killer);
        if (member != null) {
            synchronized (this) {
                member.kills(member.kills() + 1);
                team.markDirty();
            }
        }
    }

    public void recordDeath(UUID victim) {
        Team team = teamOf(victim);
        TeamMember member = team == null ? null : team.member(victim);
        if (member != null) {
            synchronized (this) {
                member.deaths(member.deaths() + 1);
                team.markDirty();
            }
        }
    }

    public void handleJoin(Player player) {
        Team team = teamOf(player.getUniqueId());
        if (team == null) {
            return;
        }
        TeamMember member = team.member(player.getUniqueId());
        synchronized (this) {
            member.name(player.getName());
            applySkin(member, player);
            team.markDirty();
        }
        String name = player.getName();
        Tasks.async(() -> broadcast(team, msg().chat("member-online", p("player", name)), player.getUniqueId()));
    }

    public void handleQuit(Player player) {
        homeCooldowns.remove(player.getUniqueId());
        playtimeCache.remove(player.getUniqueId());
    }

    private void applySkin(TeamMember member, Player player) {
        String[] skin = Heads.texture(player);
        if (skin != null) {
            member.skin(skin[0], skin[1]);
        }
    }

    // Helpers

    public void broadcast(Team team, Component message, UUID except) {
        for (UUID uuid : team.members().keySet()) {
            if (uuid.equals(except)) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    private void notifyOnline(UUID uuid, Component message) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.sendMessage(message);
        }
    }

    public Team requireTeam(Player player) {
        Team team = teamOf(player.getUniqueId());
        if (team == null) {
            msg().send(player, "not-in-team");
        }
        return team;
    }

    private boolean require(Player player, Team team, TeamPermission permission) {
        if (!team.has(player.getUniqueId(), permission)) {
            msg().send(player, "no-team-permission");
            return false;
        }
        team.markDirty();
        return true;
    }

    private boolean requireOwner(Player player, Team team) {
        if (!team.owner().equals(player.getUniqueId())) {
            msg().send(player, "owner-only");
            return false;
        }
        team.markDirty();
        return true;
    }

    private boolean requireEconomy(Player player) {
        if (!money().enabled()) {
            msg().send(player, "no-economy");
            return false;
        }
        return true;
    }

    public String nameOf(UUID uuid) {
        Team team = teamOf(uuid);
        if (team != null && team.member(uuid) != null) {
            return team.member(uuid).name();
        }
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? "Unknown" : name;
    }

    // Saving

    public void saveDirty() {
        List<Object[]> pending = new ArrayList<>();
        synchronized (this) {
            for (Team team : teams.values()) {
                if (team.dirty()) {
                    team.clean();
                    pending.add(new Object[]{team.id(), plugin.storage().snapshot(team)});
                }
            }
        }
        for (Object[] entry : pending) {
            plugin.storage().write((UUID) entry[0], (String) entry[1]);
        }
    }

    public void saveAll() {
        synchronized (this) {
            for (Team team : teams.values()) {
                team.markDirty();
            }
        }
        saveDirty();
    }
}
