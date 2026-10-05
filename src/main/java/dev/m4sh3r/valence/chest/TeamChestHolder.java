package dev.m4sh3r.valence.chest;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class TeamChestHolder implements InventoryHolder {

    private final UUID teamId;
    private Inventory inventory;

    TeamChestHolder(UUID teamId) {
        this.teamId = teamId;
    }

    public UUID teamId() {
        return teamId;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
