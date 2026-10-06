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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static dev.m4sh3r.valence.config.Messages.c;
import static dev.m4sh3r.valence.config.Messages.p;

/**
 * One shared chest per team.
 *
 * How it stays dupe free:
 * - Every item lives in exactly one place: the live chest while it is open, the team file while it is closed.
 * - Changes are saved together with the player's own inventory (at most twice a second, and on close),
 *   so after a crash the chest and the player always come back from the same moment.
 * - On Folia only one player can hold a team's chest, claimed before it opens, so two region threads
 *   never touch the same chest.
 * - If the team is disbanded while the chest is open, whoever closes it gets the items instead of a
 *   deleted team, and the owner only gets the saved items when nobody has it open.
 */
public final class TeamChests implements Listener {

    private static final boolean FOLIA = folia();

    private final Valence plugin;
    private final Map<UUID, Inventory> open = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> holders = new ConcurrentHashMap<>();
    private final Set<UUID> disbanded = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingSave = ConcurrentHashMap.newKeySet();

    public TeamChests(Valence plugin) {
        this.plugin = plugin;
    }

    /**
     * Call on the player's own thread. On a network the chest is first locked to this server and
     * reloaded from the database, so two servers never hold the same items.
     */
    public void open(Player player) {
        if (!plugin.storage().backend().networked() || open.containsKey(teamIdOf(player))) {
            openLocal(player);
            return;
        }
        Team team = plugin.teams().teamOf(player.getUniqueId());
        if (team == null) {
            openLocal(player);
            return;
        }
        UUID teamId = team.id();
        Tasks.async(() -> {
            try {
                if (!plugin.storage().backend().lockChest(teamId)) {
                    plugin.messages().send(player, "chest.other-server");
                    return;
                }
                String latest = plugin.storage().backend().load(teamId);
                if (latest != null) {
                    plugin.teams().applyRemote(teamId, latest);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Could not lock the team ender chest: " + e.getMessage());
                plugin.messages().send(player, "chest.unavailable");
                return;
            }
            Tasks.entity(player, () -> {
                openLocal(player);
                if (!open.containsKey(teamId)) {
                    unlockLater(teamId);
                }
            });
        });
    }

    private UUID teamIdOf(Player player) {
        Team team = plugin.teams().teamOf(player.getUniqueId());
        return team == null ? new UUID(0, 0) : team.id();
    }

    private void unlockLater(UUID teamId) {
        if (!plugin.storage().backend().networked()) {
            return;
        }
        Tasks.async(() -> {
            try {
                // The chest must be in the database before another server is allowed to open it.
                Team team = plugin.teams().team(teamId);
                if (team != null) {
                    plugin.teams().writeBlocking(team);
                }
                plugin.storage().backend().unlockChest(teamId);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not unlock the team ender chest: " + e.getMessage());
            }
        });
    }

    private void openLocal(Player player) {
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
            if (disbanded.contains(team.id())) {
                return;
            }
            if (FOLIA) {
                UUID holder = holders.get(team.id());
                if (holder != null && !holder.equals(player.getUniqueId())) {
                    plugin.messages().send(player, "chest.busy", p("player", plugin.teams().nameOf(holder)));
                    return;
                }
                holders.put(team.id(), player.getUniqueId());
            }
            inventory = open.computeIfAbsent(team.id(), id -> create(team));
        }
        if (player.openInventory(inventory) == null) {
            release(team.id(), player.getUniqueId(), inventory);
        }
    }

    private Inventory create(Team team) {
        TeamChestHolder holder = new TeamChestHolder(team.id());
        int size = plugin.settings().level(team.level()).chestRows() * 9;
        Inventory inventory = Bukkit.createInventory(holder, size,
                SmallCaps.component(plugin.messages().menu("chest.title", c("team", team.displayName()))));
        holder.inventory(inventory);
        List<String> data = team.chest();
        for (int i = 0; i < data.size(); i++) {
            ItemStack item = decode(data.get(i));
            if (item == null) {
                continue;
            }
            if (i < size) {
                inventory.setItem(i, item);
            } else {
                // The chest was made smaller in the config. Keep the extra items instead of deleting them.
                inventory.addItem(item);
            }
        }
        return inventory;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof TeamChestHolder holder
                && event.getWhoClicked() instanceof Player player) {
            saveSoon(holder.teamId(), player, event.getView().getTopInventory());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof TeamChestHolder holder
                && event.getWhoClicked() instanceof Player player) {
            saveSoon(holder.teamId(), player, event.getView().getTopInventory());
        }
    }

    // The click has not changed the inventory yet, so this runs a bit later on the same thread.
    // Quick clicks are bundled into one save.
    private void saveSoon(UUID teamId, Player player, Inventory inventory) {
        if (!pendingSave.add(teamId)) {
            return;
        }
        Tasks.entityLater(player, () -> {
            pendingSave.remove(teamId);
            Team team = plugin.teams().team(teamId);
            if (team != null && !disbanded.contains(teamId)) {
                commit(team, player, inventory);
            }
        }, 10);
    }

    // Player first, then the chest. If the server dies in between, an item can go missing but never doubles.
    private void commit(Team team, Player player, Inventory inventory) {
        save(team, inventory);
        player.saveData();
        plugin.teams().writeNow(team);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof TeamChestHolder holder)) {
            return;
        }
        Inventory inventory = event.getInventory();
        Team team = plugin.teams().team(holder.teamId());
        if (team == null || disbanded.contains(holder.teamId())) {
            giveBack(event.getPlayer(), inventory);
        } else if (event.getPlayer() instanceof Player player) {
            commit(team, player, inventory);
        }
        release(holder.teamId(), event.getPlayer().getUniqueId(), inventory);
    }

    private synchronized void release(UUID teamId, UUID playerId, Inventory inventory) {
        holders.remove(teamId, playerId);
        boolean othersLooking = inventory.getViewers().stream().anyMatch(v -> !v.getUniqueId().equals(playerId));
        if (!othersLooking) {
            if (open.remove(teamId, inventory)) {
                unlockLater(teamId);
            }
            if (disbanded.remove(teamId)) {
                inventory.clear();
            }
        }
    }

    private void giveBack(HumanEntity player, Inventory inventory) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir()) {
                items.add(item);
            }
        }
        inventory.clear();
        if (items.isEmpty()) {
            return;
        }
        for (ItemStack left : player.getInventory().addItem(items.toArray(new ItemStack[0])).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
        if (player instanceof Player online) {
            online.saveData();
        }
        if (player instanceof Player online) {
            plugin.messages().send(online, "chest.returned");
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
     * Called while the team is being disbanded.
     */
    public void disband(Team team, Player owner) {
        List<ItemStack> items = new ArrayList<>();
        synchronized (this) {
            disbanded.add(team.id());
            Inventory inventory = open.get(team.id());
            if (inventory != null) {
                // Someone has it open. They get the items when it closes, which we force now.
                for (HumanEntity viewer : new ArrayList<>(inventory.getViewers())) {
                    kickViewer(viewer.getUniqueId());
                }
            } else {
                disbanded.remove(team.id());
                for (String line : team.chest()) {
                    ItemStack item = decode(line);
                    if (item != null) {
                        items.add(item);
                    }
                }
            }
            team.chest(List.of());
        }
        if (items.isEmpty()) {
            return;
        }
        if (owner == null || !owner.isOnline()) {
            List<String> encoded = new ArrayList<>();
            for (ItemStack item : items) {
                encoded.add(encode(item));
            }
            UUID ownerId = team.owner();
            Tasks.async(() -> plugin.pending().add(ownerId, encoded));
            return;
        }
        Tasks.entity(owner, () -> {
            for (ItemStack left : owner.getInventory().addItem(items.toArray(new ItemStack[0])).values()) {
                owner.getWorld().dropItemNaturally(owner.getLocation(), left);
            }
            plugin.messages().send(owner, "chest.returned");
        });
    }

    public void saveOpen() {
        for (Map.Entry<UUID, Inventory> entry : open.entrySet()) {
            Team team = plugin.teams().team(entry.getKey());
            if (team != null && !disbanded.contains(entry.getKey())) {
                save(team, entry.getValue());
            }
            for (HumanEntity viewer : new ArrayList<>(entry.getValue().getViewers())) {
                viewer.closeInventory();
            }
        }
        open.clear();
        holders.clear();
    }

    private static String encode(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "";
        }
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    static ItemStack decode(String data) {
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
