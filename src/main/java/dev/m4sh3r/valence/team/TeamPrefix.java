package dev.m4sh3r.valence.team;

import dev.m4sh3r.valence.Valence;
import dev.m4sh3r.valence.util.Icons;
import dev.m4sh3r.valence.util.Tasks;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The team prefix shown in chat, the tab list and through PlaceholderAPI. Built once per team and cached.
 */
public final class TeamPrefix implements Listener {

    private final Valence plugin;
    private final Map<UUID, Component> cache = new ConcurrentHashMap<>();

    public TeamPrefix(Valence plugin) {
        this.plugin = plugin;
    }

    public Component of(Team team) {
        return cache.computeIfAbsent(team.id(), id -> MiniMessage.miniMessage().deserialize(plugin.settings().prefixFormat,
                TagResolver.resolver(
                        plugin.messages().palette(),
                        Placeholder.component("team_icon", Icons.material(team.icon())),
                        Placeholder.styling("team_color", team.textColor()),
                        Placeholder.unparsed("team_name", team.name()),
                        Placeholder.unparsed("team_tag", team.tag()),
                        Placeholder.unparsed("team_level", String.valueOf(team.level())))));
    }

    public Component of(UUID player) {
        Team team = plugin.teams().teamOf(player);
        return team == null ? Component.empty() : of(team);
    }

    /**
     * Call after a team's name, tag, color, icon or level changes.
     */
    public void refresh(Team team) {
        cache.remove(team.id());
        for (UUID member : team.members().keySet()) {
            refreshTab(member);
        }
    }

    public void forget(Team team) {
        cache.remove(team.id());
    }

    public void reload() {
        cache.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshTab(player.getUniqueId());
        }
    }

    public void refreshTab(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !plugin.settings().prefixTab) {
            return;
        }
        Tasks.entity(player, () -> {
            Team team = plugin.teams().teamOf(uuid);
            player.playerListName(team == null ? null : of(team).append(Component.text(player.getName())));
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.settings().prefixChat) {
            return;
        }
        Team team = plugin.teams().teamOf(event.getPlayer().getUniqueId());
        if (team == null) {
            return;
        }
        Component prefix = of(team);
        ChatRenderer original = event.renderer();
        event.renderer((source, displayName, message, viewer) ->
                original.render(source, prefix.append(displayName), message, viewer));
    }
}
