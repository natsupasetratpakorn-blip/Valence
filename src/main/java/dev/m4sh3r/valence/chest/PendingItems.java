package dev.m4sh3r.valence.chest;

import dev.m4sh3r.valence.Valence;
import dev.m4sh3r.valence.util.Tasks;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Items waiting for a player who was offline when they were owed them, like a disbanded team's chest.
 */
public final class PendingItems implements Listener {

    private final Valence plugin;
    private final File folder;

    public PendingItems(Valence plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "pending");
    }

    public synchronized void add(UUID player, List<String> encoded) {
        if (encoded.isEmpty()) {
            return;
        }
        try {
            if (plugin.storage().backend().addPending(player, encoded)) {
                return;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save pending items in the database, keeping them in a file", e);
        }
        File file = new File(folder, player + ".yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<String> items = new ArrayList<>(yaml.getStringList("items"));
        items.addAll(encoded);
        yaml.set("items", items);
        try {
            if (!folder.exists() && !folder.mkdirs()) {
                throw new IOException("Could not create " + folder);
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save pending items for " + player, e);
        }
    }

    private synchronized List<String> take(UUID player) {
        List<String> fromBackend = null;
        try {
            fromBackend = plugin.storage().backend().takePending(player);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read pending items from the database: " + e.getMessage());
        }
        List<String> fromFile = takeFile(player);
        if (fromBackend == null || fromBackend.isEmpty()) {
            return fromFile;
        }
        List<String> all = new ArrayList<>(fromBackend);
        all.addAll(fromFile);
        return all;
    }

    private List<String> takeFile(UUID player) {
        File file = new File(folder, player + ".yml");
        if (!file.exists()) {
            return List.of();
        }
        List<String> items = YamlConfiguration.loadConfiguration(file).getStringList("items");
        if (!file.delete()) {
            plugin.getLogger().warning("Could not delete " + file.getName() + ", items not given to avoid duplicates.");
            return List.of();
        }
        return items;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        File file = new File(folder, player.getUniqueId() + ".yml");
        if (!file.exists() && !plugin.storage().backend().networked()) {
            return;
        }
        Tasks.async(() -> {
            // Removed from disk before handing out, so a crash can lose them but never double them.
            List<String> items = take(player.getUniqueId());
            if (items.isEmpty()) {
                return;
            }
            Tasks.entity(player, () -> {
                for (String line : items) {
                    ItemStack item = TeamChests.decode(line);
                    if (item == null) {
                        continue;
                    }
                    for (ItemStack left : player.getInventory().addItem(item).values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), left);
                    }
                }
                player.saveData();
                plugin.messages().send(player, "chest.returned");
            });
        });
    }
}
