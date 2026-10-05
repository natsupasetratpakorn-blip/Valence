package dev.m4sh3r.valence.menu;

import dev.m4sh3r.valence.team.Team;
import org.bukkit.entity.Player;

public interface Menus {

    void open(Player player);

    void openCreate(Player player);

    void openMembers(Player player);

    void openBank(Player player);

    void openTop(Player player);

    void invitePopup(Player target, Team team, String inviterName);
}
