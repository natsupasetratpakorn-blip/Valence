package dev.m4sh3r.valence.menu.dialog;

import dev.m4sh3r.valence.Valence;
import dev.m4sh3r.valence.config.Messages;
import dev.m4sh3r.valence.config.Settings;
import dev.m4sh3r.valence.menu.Menus;
import dev.m4sh3r.valence.team.Invite;
import dev.m4sh3r.valence.team.LogEntry;
import dev.m4sh3r.valence.team.Team;
import dev.m4sh3r.valence.team.TeamManager;
import dev.m4sh3r.valence.team.TeamMember;
import dev.m4sh3r.valence.team.TeamPermission;
import dev.m4sh3r.valence.team.TeamRank;
import dev.m4sh3r.valence.util.Format;
import dev.m4sh3r.valence.util.Heads;
import dev.m4sh3r.valence.util.Icons;
import dev.m4sh3r.valence.util.Tasks;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

import static dev.m4sh3r.valence.config.Messages.c;
import static dev.m4sh3r.valence.config.Messages.p;

public final class DialogMenus implements Menus {

    private static final List<Material> RANK_ICONS = List.of(
            Material.LEATHER_HELMET, Material.CHAINMAIL_HELMET, Material.IRON_HELMET, Material.GOLDEN_HELMET,
            Material.DIAMOND_HELMET, Material.NETHERITE_HELMET, Material.WOODEN_SWORD, Material.IRON_SWORD,
            Material.DIAMOND_SWORD, Material.GOLDEN_SWORD, Material.BOOK, Material.WRITABLE_BOOK, Material.PAPER);

    private final Valence plugin;
    private final DialogKit kit;

    public DialogMenus(Valence plugin) {
        this.plugin = plugin;
        this.kit = new DialogKit(plugin.messages(), plugin.getLogger());
    }

    private Messages msg() {
        return plugin.messages();
    }

    private Settings settings() {
        return plugin.settings();
    }

    private TeamManager teams() {
        return plugin.teams();
    }

    private Component m(String key, TagResolver... resolvers) {
        return msg().menu(key, resolvers);
    }

    private Component t(String key, TagResolver... resolvers) {
        return msg().title(key, resolvers);
    }

    // Gives the n-th button in a list its own color.
    private Component colored(String text, int index) {
        List<net.kyori.adventure.text.format.TextColor> colors = msg().listColors();
        return Component.text(text, colors.get(index % colors.size()));
    }

    private static Component icon(Material material) {
        return Icons.material(material);
    }

    private static Component head(TeamMember member) {
        return Icons.head(member.uuid(), member.name(), member.skin(), member.skinSignature());
    }

    // Entry points

    @Override
    public void open(Player player) {
        Tasks.async(() -> openNow(player));
    }

    private void openNow(Player player) {
        Team team = teams().teamOf(player.getUniqueId());
        if (team == null) {
            noTeam(player);
        } else {
            main(player, team);
        }
    }

    @Override
    public void openCreate(Player player) {
        Tasks.async(() -> {
            if (teams().teamOf(player.getUniqueId()) != null) {
                openNow(player);
                return;
            }
            create(player, "", "", settings().defaultColor, settings().defaultIcon.name());
        });
    }

    @Override
    public void openMembers(Player player) {
        Tasks.async(() -> withTeam(player, team -> members(player, team)));
    }

    @Override
    public void openBank(Player player) {
        Tasks.async(() -> withTeam(player, team -> bank(player, team)));
    }

    @Override
    public void openTop(Player player) {
        Tasks.async(() -> top(player));
    }

    private void withTeam(Player player, java.util.function.Consumer<Team> action) {
        Team team = teams().teamOf(player.getUniqueId());
        if (team == null) {
            noTeam(player);
        } else {
            action.accept(team);
        }
    }

    // No team

    private void noTeam(Player player) {
        List<DialogBody> body = List.of(kit.item(settings().noTeamIcon, m("no-team.text")));
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(kit.button(m("no-team.create"), null, this::openCreate));
        int invites = teams().invitesOf(player.getUniqueId()).size();
        if (invites > 0) {
            buttons.add(kit.button(m("no-team.invites", p("count", invites)), null, this::invites));
        }
        buttons.add(kit.button(m("no-team.open-teams"), null, this::openTeams));
        buttons.add(kit.button(m("no-team.top"), null, this::top));
        kit.show(player, t("no-team.title"), body, List.of(), buttons, kit.closeButton(), 2);
    }

    private void create(Player player, String name, String tag, String color, String icon) {
        List<DialogInput> inputs = List.of(
                DialogInput.text("name", m("create.name")).maxLength(settings().nameMax).initial(name).width(220).build(),
                DialogInput.text("tag", m("create.tag")).maxLength(settings().tagMax).initial(tag).width(220).build(),
                DialogInput.singleOption("color", m("create.color"), colorOptions(color)).width(220).build(),
                DialogInput.singleOption("icon", m("create.icon"), iconOptions(settings().icons, icon)).width(220).build());
        List<DialogBody> body = List.of(kit.item(Settings.material(icon, settings().defaultIcon), m("create.text")));
        List<ActionButton> buttons = List.of(kit.button(m("create.button"), null, (p, view) -> {
            String newName = text(view, "name");
            String newTag = text(view, "tag");
            String newColor = text(view, "color");
            String newIcon = text(view, "icon");
            Team team = teams().create(p, newName, newTag, newColor, Settings.material(newIcon, settings().defaultIcon));
            if (team != null) {
                main(p, team);
            } else {
                create(p, newName, newTag, newColor, newIcon);
            }
        }));
        kit.show(player, t("create.title"), body, inputs, buttons, kit.back(this::open), 1);
    }

    private void invites(Player player) {
        List<Invite> invites = teams().invitesOf(player.getUniqueId());
        if (invites.isEmpty()) {
            noTeam(player);
            return;
        }
        List<DialogBody> body = new ArrayList<>();
        body.add(kit.text(m("invites.text")));
        List<ActionButton> buttons = new ArrayList<>();
        for (Invite invite : invites) {
            Team team = teams().team(invite.teamId());
            if (team == null) {
                continue;
            }
            body.add(kit.item(team.icon(), m("invites.entry", c("team", team.displayName()), p("inviter", invite.inviterName()))));
            buttons.add(kit.button(m("invites.accept").append(Component.text(" " + team.name())), null, p -> {
                if (teams().accept(p, team.name())) {
                    open(p);
                } else {
                    invites(p);
                }
            }));
            buttons.add(kit.button(m("invites.deny").append(Component.text(" " + team.name())), null, p -> {
                teams().deny(p, team.name());
                invites(p);
            }));
        }
        kit.show(player, t("invites.title"), body, List.of(), buttons, kit.back(this::noTeam), 2);
    }

    @Override
    public void invitePopup(Player target, Team team, String inviterName) {
        Tasks.async(() -> {
            List<DialogBody> body = List.of(kit.item(team.icon(),
                    m("invite-popup.text", p("player", inviterName), c("team", team.displayName()))));
            ActionButton yes = kit.button(m("invites.accept"), null, p -> {
                if (teams().accept(p, team.name())) {
                    open(p);
                } else {
                    DialogKit.close(p);
                }
            });
            ActionButton no = kit.button(m("invites.deny"), null, p -> {
                teams().deny(p, team.name());
                DialogKit.close(p);
            });
            kit.confirm(target, t("invite-popup.title"), body, yes, no);
        });
    }

    private void openTeams(Player player) {
        List<Team> list = teams().openTeams();
        List<DialogBody> body = new ArrayList<>();
        body.add(kit.text(m(list.isEmpty() ? "open-teams.empty" : "open-teams.text")));
        List<ActionButton> buttons = new ArrayList<>();
        for (Team team : list.subList(0, Math.min(list.size(), 20))) {
            buttons.add(kit.button(team.displayName(), null, p -> teamInfo(p, team, this::openTeams)));
        }
        kit.show(player, t("open-teams.title"), body, List.of(), buttons, kit.back(this::open), 2);
    }

    private void teamInfo(Player player, Team team, java.util.function.Consumer<Player> back) {
        if (teams().team(team.id()) == null) {
            back.accept(player);
            return;
        }
        List<DialogBody> body = List.of(kit.item(team.icon(), m("team-info.info",
                c("team", team.displayName()),
                p("tag", team.tag()),
                p("owner", teams().nameOf(team.owner())),
                p("level", team.level()),
                p("members", team.members().size()),
                p("max", teams().maxMembers(team)),
                p("bank", Format.money(team.bank())))));
        List<ActionButton> buttons = new ArrayList<>();
        if (team.open() && teams().teamOf(player.getUniqueId()) == null) {
            buttons.add(kit.button(m("team-info.join"), null, p -> {
                if (teams().join(p, team)) {
                    open(p);
                } else {
                    teamInfo(p, team, back);
                }
            }));
        }
        kit.show(player, t("team-info.title", c("team", team.displayName())), body, List.of(), buttons, kit.back(back), 1);
    }

    // Main menu

    private void main(Player player, Team team) {
        UUID uuid = player.getUniqueId();
        boolean owner = team.owner().equals(uuid);
        List<DialogBody> body = new ArrayList<>();
        body.add(kit.item(team.icon(), m("main.info",
                c("team", team.displayName()),
                p("tag", team.tag()),
                p("level", team.level()),
                p("members", team.members().size()),
                p("max", teams().maxMembers(team)),
                p("online", team.onlineCount()),
                p("bank", Format.money(team.bank())))));

        List<DialogInput> inputs = List.of(DialogInput.text("search", m("main.search-input"))
                .maxLength(16).width(DialogKit.SEARCH_WIDTH).build());
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(kit.button(m("main.search"), null, DialogKit.SEARCH_BUTTON_WIDTH,
                (p, view) -> reopen(p, t -> members(p, t, text(view, "search").trim()))));
        buttons.add(menuButton(m("main.members"), p -> reopen(p, t -> members(p, t))));
        buttons.add(menuButton(m("main.bank"), p -> reopen(p, t -> bank(p, t))));
        buttons.add(menuButton(m("main.top"), this::top));
        buttons.add(menuButton(m("main.players"), p -> reopen(p, t -> players(p, t))));
        if (team.has(uuid, TeamPermission.INVITE)) {
            buttons.add(menuButton(m("main.invite"), p -> reopen(p, t -> invite(p, t))));
        }
        if (team.home() != null && teams().homeUnlocked(team) && team.has(uuid, TeamPermission.USE_HOME)) {
            buttons.add(menuButton(m("main.home"), p -> {
                DialogKit.close(p);
                Tasks.entity(p, () -> teams().home(p));
            }));
        }
        if (settings().chestEnabled && team.level() >= settings().chestUnlockLevel
                && team.has(uuid, TeamPermission.ENDER_CHEST)) {
            buttons.add(menuButton(m("main.chest"), p -> {
                DialogKit.close(p);
                Tasks.entity(p, () -> plugin.chests().open(p));
            }));
        }
        if (owner) {
            buttons.add(menuButton(m("main.ranks"), p -> reopen(p, t -> ranks(p, t))));
        }
        if (team.has(uuid, TeamPermission.EDIT_SETTINGS)) {
            buttons.add(menuButton(m("main.settings"), p -> reopen(p, t -> settingsPage(p, t))));
        }
        buttons.add(menuButton(m("main.log"), p -> reopen(p, t -> log(p, t))));
        Component state = m(teams().teamChat(uuid) ? "enabled" : "disabled");
        buttons.add(menuButton(m("main.chat", c("state", state)), p -> {
            teams().toggleTeamChat(p);
            open(p);
        }));
        TeamMember self = team.member(uuid);
        Component ffState = m(self != null && self.friendlyFire() ? "enabled" : "disabled");
        buttons.add(menuButton(m("main.friendly-fire", c("state", ffState)), p -> {
            teams().toggleFriendlyFire(p);
            open(p);
        }));
        if (owner) {
            buttons.add(menuButton(m("main.disband"), p -> reopen(p, t -> confirmDisband(p, t))));
        } else {
            buttons.add(menuButton(m("main.leave"), p -> reopen(p, t -> confirmLeave(p, t))));
        }
        kit.show(player, t("main.title", c("team", team.displayName())), body, inputs, buttons, kit.closeButton(), 2);
    }

    private ActionButton menuButton(Component label, java.util.function.Consumer<Player> action) {
        return kit.button(label, null, DialogKit.BUTTON_WIDTH, (p, view) -> action.accept(p));
    }

    /**
     * Runs a page for the player's current team, or sends them to the right place if the team is gone.
     */
    private void reopen(Player player, java.util.function.Consumer<Team> page) {
        Team team = teams().teamOf(player.getUniqueId());
        if (team == null) {
            noTeam(player);
        } else {
            page.accept(team);
        }
    }

    private void confirmLeave(Player player, Team team) {
        kit.confirm(player, t("leave-confirm.title"), List.of(kit.text(m("leave-confirm.text"))),
                kit.button(m("main.leave"), null, p -> {
                    if (teams().leave(p)) {
                        noTeam(p);
                    } else {
                        open(p);
                    }
                }),
                kit.button(m("cancel"), null, this::open));
    }

    private void confirmDisband(Player player, Team team) {
        kit.confirm(player, t("disband-confirm.title"), List.of(kit.item(team.icon(), m("disband-confirm.text"))),
                kit.button(m("main.disband"), null, p -> Tasks.global(() -> {
                    boolean done = teams().disband(p);
                    Tasks.async(() -> {
                        if (done) {
                            noTeam(p);
                        } else {
                            openNow(p);
                        }
                    });
                })),
                kit.button(m("cancel"), null, this::open));
    }

    // Members

    private void members(Player player, Team team) {
        members(player, team, "");
    }

    private void members(Player player, Team team, String search) {
        String query = search.toLowerCase(Locale.ROOT);
        List<TeamMember> found = new ArrayList<>();
        for (TeamMember member : team.sortedMembers()) {
            if (query.isEmpty() || member.name().toLowerCase(Locale.ROOT).contains(query)) {
                found.add(member);
            }
        }
        // An exact name match comes first.
        found.sort(Comparator.comparing(member -> !member.name().equalsIgnoreCase(query)));
        Component text;
        if (query.isEmpty()) {
            text = m("members.text");
        } else if (found.isEmpty()) {
            text = m("members.not-found", p("search", search));
        } else {
            text = m("members.results", p("search", search), p("count", found.size()));
        }
        List<DialogBody> body = List.of(kit.item(found.isEmpty() && !query.isEmpty() ? Material.BARRIER : Material.SPYGLASS, text));
        List<ActionButton> buttons = new ArrayList<>();
        for (TeamMember member : found) {
            boolean online = Bukkit.getPlayer(member.uuid()) != null;
            TeamRank rank = team.rankOf(member.uuid());
            buttons.add(kit.button(
                    m("members.entry", c("head", head(member)), c("player", colored(member.name(), buttons.size())), p("rank", rank.name())),
                    m("members.tooltip", c("status", m(online ? "online" : "offline")), p("joined", Format.date(member.joinedAt()))),
                    p -> reopen(p, t -> profile(p, t, member.uuid()))));
        }
        kit.show(player, t("members.title"), body, List.of(), buttons, kit.back(this::open), 2);
    }

    private void profile(Player player, Team team, UUID target) {
        TeamMember member = team.member(target);
        if (member == null) {
            members(player, team);
            return;
        }
        UUID viewer = player.getUniqueId();
        boolean self = viewer.equals(target);
        boolean online = Bukkit.getPlayer(target) != null;
        TeamRank rank = team.rankOf(target);

        String balance = plugin.money().enabled() ? Format.money(teams().balance(target)) : "-";
        List<DialogBody> body = new ArrayList<>();
        body.add(kit.item(Heads.of(target, member.name(), member.skin(), member.skinSignature()), m("profile.info",
                p("player", member.name()),
                c("rank", teams().rankName(rank)),
                c("status", m(online ? "online" : "offline")))));
        body.add(kit.text(m("profile.stats",
                p("balance", balance),
                p("playtime", Format.duration(teams().playtimeSeconds(target))),
                p("joined", Format.date(member.joinedAt())),
                p("kills", member.kills()),
                p("deaths", member.deaths()),
                p("deposited", Format.money(member.deposited())))));

        List<ActionButton> buttons = new ArrayList<>();
        boolean canManage = !self && teams().outranks(team, viewer, target);
        if (canManage && team.has(viewer, TeamPermission.MANAGE_MEMBERS)) {
            buttons.add(kit.button(m("profile.promote"), null, p -> {
                teams().promote(p, target);
                reopen(p, t -> profile(p, t, target));
            }));
            buttons.add(kit.button(m("profile.demote"), null, p -> {
                teams().demote(p, target);
                reopen(p, t -> profile(p, t, target));
            }));
            buttons.add(kit.button(m("profile.set-rank"), null, p -> reopen(p, t -> rankPicker(p, t, target))));
        }
        if (!self && online) {
            buttons.add(kit.button(m("profile.message"), null, p -> reopen(p, t -> messageForm(p, t, target))));
        }
        if (canManage && team.has(viewer, TeamPermission.KICK)) {
            buttons.add(kit.button(m("profile.kick"), null, p -> reopen(p, t -> confirmKick(p, t, target))));
        }
        if (!self && team.owner().equals(viewer)) {
            buttons.add(kit.button(m("profile.transfer"), null, p -> reopen(p, t -> confirmTransfer(p, t, target))));
        }
        kit.show(player, t("profile.title", p("player", member.name())), body, List.of(), buttons,
                kit.back(p -> reopen(p, t -> members(p, t))), 2);
    }

    private void rankPicker(Player player, Team team, UUID target) {
        TeamMember member = team.member(target);
        if (member == null) {
            members(player, team);
            return;
        }
        int viewerWeight = team.weightOf(player.getUniqueId());
        TeamRank current = team.rankOf(target);
        List<DialogBody> body = List.of(kit.text(m("rank-pick.text", p("player", member.name()))));
        List<ActionButton> buttons = new ArrayList<>();
        for (TeamRank rank : team.sortedRanks()) {
            if (rank.isOwner() || rank.weight() >= viewerWeight) {
                continue;
            }
            Component label = m(rank == current ? "rank-pick.current" : "rank-pick.other",
                    c("rank_icon", icon(rank.icon())), c("name", colored(rank.name(), buttons.size())));
            buttons.add(kit.button(label, null, p -> {
                teams().setRank(p, target, rank.id());
                reopen(p, t -> profile(p, t, target));
            }));
        }
        kit.show(player, t("rank-pick.title"), body, List.of(), buttons,
                kit.back(p -> reopen(p, t -> profile(p, t, target))), 2);
    }

    private void messageForm(Player player, Team team, UUID target) {
        TeamMember member = team.member(target);
        if (member == null) {
            members(player, team);
            return;
        }
        List<DialogInput> inputs = List.of(DialogInput.text("message", m("message.input"))
                .maxLength(200).width(DialogKit.TEXT_WIDTH).build());
        List<ActionButton> buttons = List.of(kit.button(m("message.send"), null, (p, view) -> {
            teams().messageMember(p, target, text(view, "message"));
            reopen(p, t -> profile(p, t, target));
        }));
        kit.show(player, t("message.title", p("player", member.name())), List.of(), inputs, buttons,
                kit.back(p -> reopen(p, t -> profile(p, t, target))), 1);
    }

    private void confirmKick(Player player, Team team, UUID target) {
        TeamMember member = team.member(target);
        if (member == null) {
            members(player, team);
            return;
        }
        kit.confirm(player, t("kick-confirm.title", p("player", member.name())),
                List.of(kit.item(Heads.of(target, member.name(), member.skin(), member.skinSignature()), m("kick-confirm.text"))),
                kit.button(m("profile.kick"), null, p -> {
                    teams().kick(p, target);
                    reopen(p, t -> members(p, t));
                }),
                kit.button(m("cancel"), null, p -> reopen(p, t -> profile(p, t, target))));
    }

    private void confirmTransfer(Player player, Team team, UUID target) {
        TeamMember member = team.member(target);
        if (member == null) {
            members(player, team);
            return;
        }
        kit.confirm(player, t("transfer-confirm.title", p("player", member.name())),
                List.of(kit.item(Heads.of(target, member.name(), member.skin(), member.skinSignature()), m("transfer-confirm.text"))),
                kit.button(m("profile.transfer"), null, p -> {
                    teams().transfer(p, target);
                    open(p);
                }),
                kit.button(m("cancel"), null, p -> reopen(p, t -> profile(p, t, target))));
    }

    // Bank

    private void bank(Player player, Team team) {
        UUID uuid = player.getUniqueId();
        boolean owner = team.owner().equals(uuid);
        Settings.Level next = settings().nextLevel(team.level());
        Component nextText = next == null
                ? m("bank.maxed")
                : m("bank.next", p("cost", Format.money(next.cost())), p("members", next.maxMembers()));
        String balance = plugin.money().enabled() ? Format.money(teams().balance(uuid)) : "-";

        List<DialogBody> body = List.of(
                kit.item(Material.GOLD_INGOT, m("bank.info", p("bank", Format.money(team.bank())), p("balance", balance))),
                kit.text(m("bank.level", p("level", team.level()), c("next", nextText))));
        List<ActionButton> buttons = new ArrayList<>();
        if (team.has(uuid, TeamPermission.DEPOSIT)) {
            buttons.add(kit.button(m("bank.deposit"), null, p -> reopen(p, t -> amountForm(p, t, true))));
        }
        if (owner) {
            buttons.add(kit.button(m("bank.withdraw"), null, p -> reopen(p, t -> amountForm(p, t, false))));
            buttons.add(kit.button(m("bank.give"), null, p -> reopen(p, t -> giveForm(p, t))));
        }
        if (next != null && team.has(uuid, TeamPermission.UPGRADE)) {
            buttons.add(kit.button(m("bank.upgrade"), nextText, p -> {
                teams().upgrade(p);
                reopen(p, t -> bank(p, t));
            }));
        }
        kit.show(player, t("bank.title"), body, List.of(), buttons, kit.back(this::open), 2);
    }

    private void amountForm(Player player, Team team, boolean deposit) {
        String balance = plugin.money().enabled() ? Format.money(teams().balance(player.getUniqueId())) : "-";
        List<DialogBody> body = List.of(kit.item(Material.GOLD_INGOT,
                m("bank.info", p("bank", Format.money(team.bank())), p("balance", balance))));
        List<DialogInput> inputs = List.of(DialogInput.text("amount", m("bank.amount")).maxLength(16).width(200).build());
        List<ActionButton> buttons = List.of(kit.button(m("confirm"), null, (p, view) -> {
            Double amount = Format.parseAmount(text(view, "amount"));
            if (amount == null) {
                msg().send(p, "invalid-amount");
                reopen(p, t -> amountForm(p, t, deposit));
                return;
            }
            Tasks.global(() -> {
                if (deposit) {
                    teams().deposit(p, amount);
                } else {
                    teams().withdraw(p, amount);
                }
                Tasks.async(() -> reopen(p, t -> bank(p, t)));
            });
        }));
        kit.show(player, t(deposit ? "bank.deposit-title" : "bank.withdraw-title"), body, inputs, buttons,
                kit.back(p -> reopen(p, t -> bank(p, t))), 1);
    }

    private void giveForm(Player player, Team team) {
        Map<String, String> people = new LinkedHashMap<>();
        for (TeamMember member : team.sortedMembers()) {
            people.put(member.uuid().toString(), member.name());
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            people.putIfAbsent(online.getUniqueId().toString(), online.getName());
        }
        List<String> ids = new ArrayList<>(people.keySet());
        List<Component> labels = new ArrayList<>();
        for (String id : ids) {
            labels.add(Component.text(people.get(id)));
        }
        List<DialogInput> inputs = List.of(
                DialogInput.singleOption("player", m("bank.player"), DialogKit.options(ids, labels, ids.get(0))).width(200).build(),
                DialogInput.text("amount", m("bank.amount")).maxLength(16).width(200).build());
        List<DialogBody> body = List.of(kit.item(Material.GOLD_INGOT,
                m("bank.info", p("bank", Format.money(team.bank())), p("balance", "-"))));
        List<ActionButton> buttons = List.of(kit.button(m("confirm"), null, (p, view) -> {
            Double amount = Format.parseAmount(text(view, "amount"));
            String id = text(view, "player");
            if (amount == null) {
                msg().send(p, "invalid-amount");
                reopen(p, t -> giveForm(p, t));
                return;
            }
            UUID targetId;
            try {
                targetId = UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                msg().send(p, "player-not-found", p("player", id));
                reopen(p, t -> bank(p, t));
                return;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
            Tasks.global(() -> {
                teams().give(p, target, amount);
                Tasks.async(() -> reopen(p, t -> bank(p, t)));
            });
        }));
        kit.show(player, t("bank.give-title"), body, inputs, buttons, kit.back(p -> reopen(p, t -> bank(p, t))), 1);
    }

    // Leaderboards

    private void top(Player player) {
        List<Team> list = teams().topTeams();
        List<DialogBody> body = new ArrayList<>();
        body.add(kit.text(m(list.isEmpty() ? "top.empty" : "top.text")));
        int position = 1;
        for (Team team : list) {
            body.add(kit.item(team.icon(), m("top.entry",
                    p("position", position++),
                    c("team", team.displayName()),
                    p("bank", Format.money(team.bank())),
                    p("level", team.level()))));
        }
        kit.show(player, t("top.title"), body, List.of(), List.of(), kit.back(this::open), 1);
    }

    private void players(Player player, Team team) {
        List<TeamMember> members = new ArrayList<>(team.members().values());
        List<DialogBody> body = new ArrayList<>();
        body.add(kit.item(Material.IRON_SWORD, ranking(m("players.kills"), members, member -> member.kills(),
                value -> String.valueOf((long) value))));
        if (plugin.money().enabled()) {
            Map<UUID, Double> balances = new LinkedHashMap<>();
            for (TeamMember member : members) {
                balances.put(member.uuid(), teams().balance(member.uuid()));
            }
            body.add(kit.item(Material.EMERALD, ranking(m("players.money"), members,
                    member -> balances.get(member.uuid()), Format::money)));
        }
        body.add(kit.item(Material.GOLD_INGOT, ranking(m("players.deposits"), members, TeamMember::deposited, Format::money)));
        kit.show(player, t("players.title"), body, List.of(), List.of(), kit.back(this::open), 1);
    }

    private Component ranking(Component header, List<TeamMember> members, ToDoubleFunction<TeamMember> value,
                              java.util.function.DoubleFunction<String> format) {
        List<TeamMember> sorted = new ArrayList<>(members);
        sorted.sort(Comparator.comparingDouble(value).reversed());
        Component text = header;
        int position = 1;
        for (TeamMember member : sorted.subList(0, Math.min(5, sorted.size()))) {
            text = text.append(Component.newline()).append(m("players.entry",
                    p("position", position++),
                    p("player", member.name()),
                    p("value", format.apply(value.applyAsDouble(member)))));
        }
        return text;
    }

    // Ranks

    private void ranks(Player player, Team team) {
        List<DialogBody> body = new ArrayList<>();
        body.add(kit.text(m("ranks.text")));
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (TeamMember member : team.members().values()) {
            counts.merge(team.rankOf(member.uuid()).id(), 1, Integer::sum);
        }
        List<ActionButton> buttons = new ArrayList<>();
        for (TeamRank rank : team.sortedRanks()) {
            body.add(kit.item(rank.icon(), m("ranks.entry",
                    c("rank", teams().rankName(rank)),
                    p("weight", rank.weight()),
                    c("count", m(counts.getOrDefault(rank.id(), 0) == 1 ? "ranks.one" : "ranks.many",
                            p("count", counts.getOrDefault(rank.id(), 0)))))));
            buttons.add(kit.button(m("ranks.button", c("rank_icon", icon(rank.icon())), c("name", colored(rank.name(), buttons.size()))), null,
                    p -> reopen(p, t -> rankEditor(p, t, rank.id()))));
        }
        if (team.ranks().size() < TeamManager.MAX_RANKS) {
            buttons.add(kit.button(m("ranks.new"), null, p -> reopen(p, t -> rankEditor(p, t, null))));
        }
        kit.show(player, t("ranks.title"), body, List.of(), buttons, kit.back(this::open), 2);
    }

    private void rankEditor(Player player, Team team, String rankId) {
        TeamRank rank = rankId == null ? null : team.rank(rankId);
        if (rankId != null && rank == null) {
            ranks(player, team);
            return;
        }
        String name = rank == null ? "" : rank.name();
        Material icon = rank == null ? Material.IRON_HELMET : rank.icon();
        int weight = rank == null ? 50 : rank.weight();

        List<Material> icons = new ArrayList<>(RANK_ICONS);
        for (Material material : settings().icons) {
            // Block icons have no flat picture to show inside a button, so ranks stick to items.
            if (!icons.contains(material) && !material.isBlock()) {
                icons.add(material);
            }
        }
        if (!icons.contains(icon)) {
            icons.add(0, icon);
        }

        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogInput.text("name", m("rank-edit.name")).maxLength(16).initial(name).width(220).build());
        inputs.add(DialogInput.singleOption("icon", m("rank-edit.icon"), iconOptions(icons, icon.name())).width(220).build());
        if (rank == null || !rank.builtIn()) {
            inputs.add(DialogInput.numberRange("weight", m("rank-edit.weight"), 1, 99)
                    .step(1f).initial((float) weight).width(220).labelFormat("%s: %s").build());
        }
        if (rank == null || !rank.isOwner()) {
            for (TeamPermission permission : TeamPermission.values()) {
                inputs.add(DialogInput.bool("perm_" + permission.key().replace('-', '_'),
                                msg().parse("<main><text>", p("text", msg().menuRaw("permission." + permission.key()))))
                        .initial(rank != null && rank.permissions().contains(permission))
                        .build());
            }
        }

        List<DialogBody> body = new ArrayList<>();
        body.add(kit.item(icon, rank == null ? t("rank-edit.new-title") : teams().rankName(rank)));
        if (rank != null && rank.builtIn()) {
            body.add(kit.text(m("rank-edit.locked")));
        }

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(kit.button(m("save"), null, (p, view) -> {
            String newName = text(view, "name");
            Material newIcon = Settings.material(text(view, "icon"), Material.PAPER);
            Float rawWeight = view.getFloat("weight");
            int newWeight = rawWeight == null ? weight : Math.round(rawWeight);
            Set<TeamPermission> permissions = EnumSet.noneOf(TeamPermission.class);
            for (TeamPermission permission : TeamPermission.values()) {
                Boolean value = view.getBoolean("perm_" + permission.key().replace('-', '_'));
                if (Boolean.TRUE.equals(value)) {
                    permissions.add(permission);
                }
            }
            boolean ok = rank == null
                    ? teams().createRank(p, newName, newIcon, newWeight, permissions) != null
                    : teams().updateRank(p, rank.id(), newName, newIcon, newWeight, permissions);
            if (ok) {
                reopen(p, t -> ranks(p, t));
            } else {
                reopen(p, t -> rankEditor(p, t, rankId));
            }
        }));
        if (rank != null && !rank.builtIn()) {
            buttons.add(kit.button(m("rank-edit.delete"), null, p -> {
                teams().deleteRank(p, rank.id());
                reopen(p, t -> ranks(p, t));
            }));
        }
        kit.show(player, t(rank == null ? "rank-edit.new-title" : "rank-edit.title"), body, inputs, buttons,
                kit.back(p -> reopen(p, t -> ranks(p, t))), 2);
    }

    // Settings

    private void settingsPage(Player player, Team team) {
        List<DialogInput> inputs = List.of(
                DialogInput.text("name", m("settings.name")).maxLength(settings().nameMax).initial(team.name()).width(220).build(),
                DialogInput.text("tag", m("settings.tag")).maxLength(settings().tagMax).initial(team.tag()).width(220).build(),
                DialogInput.singleOption("color", m("settings.color"), colorOptions(team.color())).width(220).build(),
                DialogInput.singleOption("icon", m("settings.icon"), iconOptions(withIcon(settings().icons, team.icon()), team.icon().name())).width(220).build(),
                DialogInput.bool("open", m("settings.open")).initial(team.open()).build());
        List<DialogBody> body = List.of(kit.item(team.icon(), team.displayName()));
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(kit.button(m("save"), null, (p, view) -> {
            boolean ok = teams().updateSettings(p,
                    text(view, "name"),
                    text(view, "tag"),
                    text(view, "color"),
                    Settings.material(text(view, "icon"), null),
                    Boolean.TRUE.equals(view.getBoolean("open")));
            if (ok) {
                open(p);
            } else {
                reopen(p, t -> settingsPage(p, t));
            }
        }));
        if (team.has(player.getUniqueId(), TeamPermission.SET_HOME) && teams().homeUnlocked(team)) {
            buttons.add(kit.button(m("settings.set-home"), null, p -> Tasks.entity(p, () -> {
                teams().setHome(p);
                Tasks.async(() -> reopen(p, t -> settingsPage(p, t)));
            })));
        }
        kit.show(player, t("settings.title"), body, inputs, buttons, kit.back(this::open), 2);
    }

    // Invite

    private void invite(Player player, Team team) {
        List<Player> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player) && teams().teamOf(online.getUniqueId()) == null && player.canSee(online)) {
                candidates.add(online);
            }
        }
        candidates.sort(Comparator.comparing(online -> online.getName().toLowerCase(Locale.ROOT)));
        if (candidates.size() > 50) {
            candidates = candidates.subList(0, 50);
        }
        if (candidates.isEmpty()) {
            kit.show(player, t("invite.title"), List.of(kit.item(Material.PAPER, m("invite.nobody"))), List.of(),
                    List.of(), kit.back(this::open), 1);
            return;
        }
        List<String> ids = new ArrayList<>();
        List<Component> labels = new ArrayList<>();
        for (Player candidate : candidates) {
            ids.add(candidate.getUniqueId().toString());
            labels.add(Component.text(candidate.getName()));
        }
        List<DialogInput> inputs = List.of(DialogInput.singleOption("player", m("invite.player"),
                DialogKit.options(ids, labels, ids.get(0))).width(220).build());
        List<ActionButton> buttons = List.of(kit.button(m("invite.send"), null, (p, view) -> {
            Player target = null;
            try {
                target = Bukkit.getPlayer(UUID.fromString(text(view, "player")));
            } catch (IllegalArgumentException ignored) {
            }
            if (target == null) {
                msg().send(p, "player-not-found", p("player", "?"));
            } else {
                teams().invite(p, target);
            }
            open(p);
        }));
        kit.show(player, t("invite.title"), List.of(kit.item(Material.WRITABLE_BOOK, m("invite.text"))), inputs,
                buttons, kit.back(this::open), 1);
    }

    // Activity

    private void log(Player player, Team team) {
        List<DialogBody> body = new ArrayList<>();
        Component lines = Component.empty();
        int shown = 0;
        for (LogEntry entry : team.log()) {
            if (shown == 15) {
                break;
            }
            if (shown > 0) {
                lines = lines.append(Component.newline());
            }
            lines = lines.append(m("log.entry", p("time", Format.ago(entry.time())), p("text", entry.text())));
            shown++;
        }
        body.add(kit.item(Material.BOOK, m(shown == 0 ? "log.empty" : "log.text")));
        if (shown > 0) {
            body.add(kit.text(lines));
        }
        kit.show(player, t("log.title"), body, List.of(), List.of(), kit.back(this::open), 1);
    }

    // Shared helpers

    private List<io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry> colorOptions(String selected) {
        List<String> ids = new ArrayList<>(settings().colors.keySet());
        List<Component> labels = new ArrayList<>();
        for (String id : ids) {
            labels.add(Component.text(id, settings().colors.get(id)));
        }
        return DialogKit.options(ids, labels, selected);
    }

    private List<io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry> iconOptions(List<Material> icons, String selected) {
        List<String> ids = new ArrayList<>();
        List<Component> labels = new ArrayList<>();
        for (Material material : icons) {
            ids.add(material.name());
            labels.add(msg().parse("<main><name>", p("name", pretty(material))));
        }
        return DialogKit.options(ids, labels, selected);
    }

    private static List<Material> withIcon(List<Material> icons, Material icon) {
        if (icons.contains(icon)) {
            return icons;
        }
        List<Material> list = new ArrayList<>(icons);
        list.add(0, icon);
        return list;
    }

    private static String pretty(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private static String text(DialogResponseView view, String key) {
        String value = view.getText(key);
        return value == null ? "" : value;
    }
}
