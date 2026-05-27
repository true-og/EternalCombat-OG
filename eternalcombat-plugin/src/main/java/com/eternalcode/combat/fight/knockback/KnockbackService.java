package com.eternalcode.combat.fight.knockback;

import com.eternalcode.combat.config.implementation.PluginConfig;
import com.eternalcode.combat.region.Region;
import com.eternalcode.combat.region.RegionProvider;
import com.eternalcode.commons.bukkit.scheduler.MinecraftScheduler;
import io.papermc.lib.PaperLib;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.util.Vector;

public final class KnockbackService {

    // Pushed just past the nearest region wall so the player lands outside the cuboid.
    private static final double EJECT_MARGIN = 1.5;

    private final PluginConfig config;
    private final MinecraftScheduler scheduler;
    private final RegionProvider regionProvider;

    private final Map<UUID, Region> insideRegion = new HashMap<>();

    public KnockbackService(PluginConfig config, MinecraftScheduler scheduler, RegionProvider regionProvider) {
        this.config = config;
        this.scheduler = scheduler;
        this.regionProvider = regionProvider;
    }

    public void knockbackLater(Region region, Player player, Duration duration) {
        this.scheduler.runLater(() -> this.knockback(region, player), duration);
    }

    // Push the player along the given horizontal direction. Used when a move into a safe zone is
    // cancelled: the direction is (from - to), i.e. straight back the way the player came, so they
    // bounce back into the legal area (wilderness or warzone) regardless of the safe region's shape
    // or size. Applied a tick later so it survives the move cancellation.
    public void knockbackDirectionLater(Player player, Vector horizontalDirection, Duration duration) {
        Vector direction = new Vector(horizontalDirection.getX(), 0, horizontalDirection.getZ());
        if (direction.lengthSquared() < 1.0E-6) {
            return;
        }

        double multiplier = this.config.knockback.multiplier;
        Vector velocity = direction.normalize().multiply(multiplier);

        this.scheduler.runLater(
            () -> applyVelocity(player, new Vector(velocity.getX(), 0.5, velocity.getZ())),
            duration
        );
    }

    public void forceKnockbackLater(Player player, Region region) {
        if (insideRegion.containsKey(player.getUniqueId())) {
            return;
        }

        insideRegion.put(player.getUniqueId(), region);

        scheduler.runLater(player.getLocation(), () -> {
            insideRegion.remove(player.getUniqueId());

            Location playerLocation = player.getLocation();
            if (!region.contains(playerLocation) && !regionProvider.isInRegion(playerLocation)) {
                return;
            }

            // Eject through the nearest wall, keeping the player's own Y. No highest-block lookup
            // (which launched players onto walls/treetops) and no bounding-box perimeter teleport
            // (which threw them dozens of blocks across large regions). A mounted player is moved
            // by teleporting their vehicle, so the mount travels with them instead of dismounting.
            Location target = ejectLocation(region, playerLocation);
            Entity vehicle = topVehicle(player);
            if (vehicle != null) {
                PaperLib.teleportAsync(vehicle, target, TeleportCause.PLUGIN);
            } else {
                PaperLib.teleportAsync(player, target, TeleportCause.PLUGIN);
            }
        }, this.config.knockback.forceDelay);
    }

    public void knockback(Region region, Player player) {
        Vector outward = outwardDirection(region, player.getLocation());
        double multiplier = this.config.knockback.multiplier;

        applyVelocity(player, new Vector(outward.getX() * multiplier, 0.5, outward.getZ() * multiplier));
    }

    // Apply velocity to the player, or to the vehicle they are riding so the mount (and its rider)
    // move together. Never dismounts the player.
    private static void applyVelocity(Player player, Vector velocity) {
        Entity vehicle = topVehicle(player);
        if (vehicle != null) {
            vehicle.setVelocity(velocity);
        } else {
            player.setVelocity(velocity);
        }
    }

    // The root of the player's mount stack (the entity actually being moved), or null when the
    // player is on foot.
    private static Entity topVehicle(Player player) {
        Entity vehicle = player.getVehicle();
        if (vehicle == null) {
            return null;
        }
        while (vehicle.getVehicle() != null) {
            vehicle = vehicle.getVehicle();
        }
        return vehicle;
    }

    // Unit vector pointing straight out of the closest vertical face of the region. For a player
    // crossing a wall this is the wall they crossed, so the push always sends them back the way
    // they came regardless of how large the region is.
    private static Vector outwardDirection(Region region, Location location) {
        Face face = nearestFace(region, location);
        return switch (face.side) {
            case WEST -> new Vector(-1, 0, 0);
            case EAST -> new Vector(1, 0, 0);
            case NORTH -> new Vector(0, 0, -1);
            case SOUTH -> new Vector(0, 0, 1);
        };
    }

    private static Location ejectLocation(Region region, Location location) {
        Face face = nearestFace(region, location);

        double minX = region.getMin().getX();
        double maxX = region.getMax().getX() + 1;
        double minZ = region.getMin().getZ();
        double maxZ = region.getMax().getZ() + 1;

        double x = location.getX();
        double z = location.getZ();

        switch (face.side) {
            case WEST -> x = minX - EJECT_MARGIN;
            case EAST -> x = maxX + EJECT_MARGIN;
            case NORTH -> z = minZ - EJECT_MARGIN;
            case SOUTH -> z = maxZ + EJECT_MARGIN;
        }

        return new Location(location.getWorld(), x, location.getY(), z, location.getYaw(), location.getPitch());
    }

    private static Face nearestFace(Region region, Location location) {
        double minX = region.getMin().getX();
        double maxX = region.getMax().getX() + 1;
        double minZ = region.getMin().getZ();
        double maxZ = region.getMax().getZ() + 1;

        double px = location.getX();
        double pz = location.getZ();

        double west = px - minX;
        double east = maxX - px;
        double north = pz - minZ;
        double south = maxZ - pz;

        double nearest = Math.min(Math.min(west, east), Math.min(north, south));
        if (nearest == west) {
            return new Face(Side.WEST, west);
        }
        if (nearest == east) {
            return new Face(Side.EAST, east);
        }
        if (nearest == north) {
            return new Face(Side.NORTH, north);
        }
        return new Face(Side.SOUTH, south);
    }

    private enum Side { WEST, EAST, NORTH, SOUTH }

    private record Face(Side side, double distance) {}
}
