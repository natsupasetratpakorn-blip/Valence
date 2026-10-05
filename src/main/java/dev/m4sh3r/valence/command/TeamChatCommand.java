package dev.m4sh3r.valence.command;

import dev.m4sh3r.valence.Valence;
import dev.m4sh3r.valence.util.Tasks;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class TeamChatCommand implements CommandExecutor {

    private final Valence plugin;

    public TeamChatCommand(Valence plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return true;
        }
        String message = String.join(" ", args);
        Tasks.async(() -> {
            if (message.isEmpty()) {
                plugin.teams().toggleTeamChat(player);
            } else {
                plugin.teams().sendTeamChat(player, message);
            }
        });
        return true;
    }
}
