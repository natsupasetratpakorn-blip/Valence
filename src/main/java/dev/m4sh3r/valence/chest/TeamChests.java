package dev.m4sh3r.valence.chest;

import dev.m4sh3r.valence.Valence;
import dev.m4sh3r.valence.team.Team;
import dev.m4sh3r.valence.team.TeamPermission;
import dev.m4sh3r.valence.util.SmallCaps;
import dev.m4sh3r.valence.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static dev.m4sh3r.valence.config.Messages.c;
import static dev.m4sh3r.valence.config.Messages.p;

/**
 * One shared chest per team. Items are saved as text in the team file whenever someone closes it.
 */
public final class TeamChests implements Listener {

    // Folia runs players in different regions on different threads, so only one person may use a chest at a time there.
    private static final boolean FOLIA = folia();

    private final Valence plugin;
    private final Map<UUID, Inventory> open = new ConcurrentHashMap<>();

    public TeamChests(Valence plugin) {
        this.plugin = plugin;
    }

    /**
     * Call on the player's own thread.
     */
    public void open(Player player) {
        Team team = plugin.teams().requireTeam(player);
        if (team == null) {
            return;
        }
        if (!plugin.settings().chestEnabled) {
            plugin.messages().send(player, "chest.disabled");
            return;
        }
        if (team.level() < plugin.settings().chestUnlockLevel) {
            plugin.messages().send(player, "chest.locked", p("level", plugin.settings().chestUnlockLevel));
            return;
        }
        if (!team.has(player.getUniqueId(), TeamPermission.ENDER_CHEST)) {
            plugin.messages().send(player, "no-team-permission");
            return;
        }
        Inventory inventory;
        synchronized (this) {
            inventory = open.get(team.id());
            if (inventory != null && FOLIA && !inventory.getViewers().isEmpty()) {
                plugin.messages().send(player, "chest.busy", p("player", inventory.getViewers().get(0).getName()));
                return;
            }
            if (inventory == null) {
                inventory = create(team);
                open.put(team.id(), inventory);
            }
        }
        player.openInventory(inventory);
    }

    private Inventory create(Team team) {
        TeamChestHolder holder = new TeamChestHolder(team.id());
        int size = plugin.settings().chestRows * 9;
        Inventory inventory = Bukkit.createInventory(holder, size,
                SmallCaps.component(plugin.messages().menu("chest.title", c("team", team.displayName()))));
        holder.inventory(inventory);
        List<String> data = team.chest();
        for (int i = 0; i < Math.min(size, data.size()); i++) {
            ItemStack item = decode(data.get(i));
            if (item != null) {
                inventory.setItem(i, item);
            }
        }
        return inventory;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof TeamChestHolder holder)) {
            return;
        }
        Team team = plugin.teams().team(holder.teamId());
        if (team != null) {
            save(team, event.getInventory());
        }
        synchronized (this) {
            boolean othersLooking = event.getInventory().getViewers().stream().anyMatch(v -> !v.equals(event.getPlayer()));
            if (!othersLooking) {
                open.remove(holder.teamId(), event.getInventory());
            }
        }
    }

    private void save(Team team, Inventory inventory) {
        List<String> data = new ArrayList<>(inventory.getSize());
        for (ItemStack item : inventory.getContents()) {
            data.add(encode(item));
        }
        synchronized (plugin.teams()) {
            team.chest(data);
            team.markDirty();
        }
    }

    /**
     * Closes the chest for someone who just left or was kicked from the team.
     */
    public void kickViewer(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            Tasks.entity(player, () -> {
                if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof TeamChestHolder) {
                    player.closeInventory();
                }
            });
        }
    }

    /**
     * Empties a disbanded team's chest into the owner's inventory, dropping whatever doesn't fit.
     */
    public void disband(Team team, Player owner) {
        Inventory inventory = open.remove(team.id());
        List<ItemStack> items = new ArrayList<>();
        if (inventory != null) {
            for (HumanEntity viewer : new ArrayList<>(inventory.getViewers())) {
                if (viewer instanceof Player player && !player.equals(owner)) {
                    kickViewer(player.getUniqueId());
                }
            }
            for (ItemStack item : inventory.getContents()) {
                if (item != null) {
                    items.add(item.clone());
                }
            }
            inventory.clear();
        } else {
            for (String line : team.chest()) {
                ItemStack item = decode(line);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        team.chest(List.of());
        if (owner == null || items.isEmpty()) {
            return;
        }
        Tasks.entity(owner, () -> {
            if (owner.getOpenInventory().getTopInventory().getHolder(false) instanceof TeamChestHolder) {
                owner.closeInventory();
            }
            for (ItemStack left : owner.getInventory().addItem(items.toArray(new ItemStack[0])).values()) {
                owner.getWorld().dropItemNaturally(owner.getLocation(), left);
            }
            plugin.messages().send(owner, "chest.returned");
        });
    }

    public void saveOpen() {
        for (Map.Entry<UUID, Inventory> entry : open.entrySet()) {
            Team team = plugin.teams().team(entry.getKey());
            if (team != null) {
                save(team, entry.getValue());
            }
            for (HumanEntity viewer : new ArrayList<>(entry.getValue().getViewers())) {
                viewer.closeInventory();
            }
        }
        open.clear();
    }

    private static String encode(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "";
        }
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    private static ItemStack decode(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean folia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
