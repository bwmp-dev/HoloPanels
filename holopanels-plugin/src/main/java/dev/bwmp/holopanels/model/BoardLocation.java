package dev.bwmp.holopanels.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.Optional;

public record BoardLocation(String world, double x, double y, double z, float yaw, float pitch) {
    public Optional<Location> resolve() {
        return Optional.ofNullable(Bukkit.getWorld(world))
                .map(value -> new Location(value, x, y, z, yaw, pitch));
    }
}
