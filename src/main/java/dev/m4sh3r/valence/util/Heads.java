package dev.m4sh3r.valence.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

public final class Heads {

    private Heads() {
    }

    /**
     * Builds a player head from a stored skin so it works for offline players without a web request.
     */
    public static ItemStack of(UUID uuid, String name, String texture, String signature) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (!(head.getItemMeta() instanceof SkullMeta meta)) {
            return head;
        }
        Player online = Bukkit.getPlayer(uuid);
        PlayerProfile profile;
        if (online != null && texture(online) != null) {
            profile = online.getPlayerProfile();
        } else if (texture != null) {
            profile = Bukkit.createProfile(uuid, name);
            profile.setProperty(new ProfileProperty("textures", texture, signature));
        } else {
            // No skin known (offline mode servers). A plain head shows the default skin instead of a missing texture.
            return head;
        }
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }

    public static String[] texture(Player player) {
        for (ProfileProperty property : player.getPlayerProfile().getProperties()) {
            if (property.getName().equals("textures")) {
                return new String[]{property.getValue(), property.getSignature()};
            }
        }
        return null;
    }
}
