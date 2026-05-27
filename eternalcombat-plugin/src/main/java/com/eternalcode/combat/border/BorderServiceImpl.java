package com.eternalcode.combat.border;

import com.eternalcode.combat.border.event.BorderHideAsyncEvent;
import com.eternalcode.combat.border.event.BorderShowAsyncEvent;
import com.eternalcode.combat.event.EventManager;
import com.eternalcode.combat.region.Region;
import com.eternalcode.combat.region.RegionProvider;
import com.eternalcode.commons.bukkit.scheduler.MinecraftScheduler;
import dev.rollczi.litecommands.shared.Lazy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class BorderServiceImpl implements BorderService {

    // Horizontal neighbour offsets (west, east, north, south). The border is a vertical wall, so only
    // horizontal transitions between safe and unsafe space matter.
    private static final int[][] HORIZONTAL_NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final MinecraftScheduler scheduler;
    private final EventManager eventManager;
    private final RegionProvider regionProvider;

    private final Supplier<BorderSettings> settings;

    private final BorderTriggerIndex borderIndexes;
    private final BorderActivePointsIndex activeBorderIndex = new BorderActivePointsIndex();

    public BorderServiceImpl(MinecraftScheduler scheduler, Server server, RegionProvider provider, EventManager eventManager, Supplier<BorderSettings> settings) {
        this.scheduler = scheduler;
        this.eventManager = eventManager;
        this.regionProvider = provider;
        this.settings = settings;
        this.borderIndexes = BorderTriggerIndex.started(server, scheduler, provider, settings);
    }

    @Override
    public void updateBorder(Player player, Location location) {
        Optional<BorderResult> result = resolveBorder(location);
        String world = player.getWorld().getName();

        if (result.isEmpty()) {
            if (!this.activeBorderIndex.hasPoints(world, player.getUniqueId())) {
                return;
            }

            this.clearBorder(player);
            return;
        }

        scheduler.runAsync(() -> {
            BorderResult borderResult = result.get();
            Set<BorderPoint> points = borderResult.collect();

            if (!points.isEmpty()) {
                BorderShowAsyncEvent event = eventManager.publishEvent(new BorderShowAsyncEvent(player, points));
                points = event.getPoints();
            }

            Set<BorderPoint> removed = this.activeBorderIndex.putPoints(world, player.getUniqueId(), points);

            if (!removed.isEmpty()) {
                eventManager.publishEvent(new BorderHideAsyncEvent(player, removed));
            }
        });
    }

    @Override
    public void clearBorder(Player player) {
        World world = player.getWorld();
        UUID uniqueId = player.getUniqueId();

        scheduler.runAsync(() -> {
            Set<BorderPoint> removed = this.activeBorderIndex.removePoints(world.getName(), uniqueId);
            if (!removed.isEmpty()) {
                eventManager.publishEvent(new BorderHideAsyncEvent(player, removed));
            }
        });
    }

    @Override
    public Set<BorderPoint> getActiveBorder(Player player) {
        return this.activeBorderIndex.getPoints(player.getWorld().getName(), player.getUniqueId());
    }

    private Optional<BorderResult> resolveBorder(Location location) {
        // Cheap gate: only build a border when the player is near a restricted region at all.
        if (borderIndexes.getTriggered(location).isEmpty()) {
            return Optional.empty();
        }

        BorderLazyResult result = new BorderLazyResult();
        result.addLazyBorderPoints(new Lazy<>(() -> this.resolveBorderPoints(location)));
        return Optional.of(result);
    }

    // Render glass on the true boundary between safe (PvP-denied) and unsafe (PvP-allowed/wilderness)
    // space, rather than around a region's bounding box. A wall block is placed on a safe column only
    // when an adjacent column is unsafe, so the wall appears between the warzone and the safe spawn or
    // market, but never between the warzone and the wilderness (both unsafe), and never on the outer
    // face of a large umbrella region that merely encloses the warzone.
    private List<BorderPoint> resolveBorderPoints(Location playerLocation) {
        World world = playerLocation.getWorld();
        int px = (int) Math.round(playerLocation.getX());
        int py = (int) Math.round(playerLocation.getY());
        int pz = (int) Math.round(playerLocation.getZ());
        int distance = settings.get().distanceRounded();

        // Sample, per column near the player, the safe region present at the player's Y (null = unsafe).
        // Sampling one Y keeps this cheap; safe regions are effectively full-height in practice.
        Map<Long, Region> columns = new HashMap<>();
        for (int dx = -distance - 1; dx <= distance + 1; dx++) {
            for (int dz = -distance - 1; dz <= distance + 1; dz++) {
                int cx = px + dx;
                int cz = pz + dz;
                Region region = this.regionProvider.getRegion(new Location(world, cx, py, cz)).orElse(null);
                columns.put(key(cx, cz), region);
            }
        }

        List<BorderPoint> points = new ArrayList<>();
        for (int dx = -distance; dx <= distance; dx++) {
            for (int dz = -distance; dz <= distance; dz++) {
                int cx = px + dx;
                int cz = pz + dz;

                // Only unsafe columns (where the player can stand) cast a wall onto safe neighbours.
                if (columns.get(key(cx, cz)) != null) {
                    continue;
                }

                for (int[] neighbour : HORIZONTAL_NEIGHBOURS) {
                    int nx = cx + neighbour[0];
                    int nz = cz + neighbour[1];

                    Region safeRegion = columns.get(key(nx, nz));
                    if (safeRegion == null) {
                        continue;
                    }

                    int minY = Math.max(py - distance, safeRegion.getMin().getBlockY());
                    int maxY = Math.min(py + distance, safeRegion.getMax().getBlockY());
                    for (int y = minY; y <= maxY; y++) {
                        if (isVisible(nx, y, nz, playerLocation)) {
                            points.add(new BorderPoint(nx, y, nz));
                        }
                    }
                }
            }
        }

        return points;
    }

    private static long key(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private boolean isVisible(int x, int y, int z, Location player) {
        return Math.sqrt(Math.pow(x - player.getX(), 2) + Math.pow(y - player.getY(), 2) + Math.pow(z - player.getZ(), 2)) <= this.settings.get().distance;
    }

}
