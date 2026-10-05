package dev.m4sh3r.valence.team;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record TeamHome(String world, double x, double y, double z, float yaw, float pitch) {

    public static TeamHome of(Location location) {
        return new TeamHome(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    public Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z, yaw, pitch);
    }
}
