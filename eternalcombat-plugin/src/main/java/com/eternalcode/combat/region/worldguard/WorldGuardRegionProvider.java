package com.eternalcode.combat.region.worldguard;

import com.eternalcode.combat.config.implementation.PluginConfig;
import com.eternalcode.combat.region.Region;
import com.eternalcode.combat.region.RegionProvider;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.TreeSet;
import org.bukkit.Location;

import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

public class WorldGuardRegionProvider implements RegionProvider {

    private final RegionContainer regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();
    private final TreeSet<String> regions = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private final PluginConfig pluginConfig;

    public WorldGuardRegionProvider(PluginConfig pluginConfig) {
        this.regions.addAll(pluginConfig.regions.blockedRegions);
        this.pluginConfig = pluginConfig;
    }

    @Override
    public Optional<Region> getRegion(Location location) {
        RegionQuery regionQuery = this.regionContainer.createQuery();
        ApplicableRegionSet applicableRegions = regionQuery.getApplicableRegions(BukkitAdapter.adapt(location));

        if (applicableRegions.size() == 0) {
            return Optional.empty();
        }

        if (!this.isRestricted(applicableRegions)) {
            return Optional.empty();
        }

        // Eject relative to the innermost (highest-priority) region the player is standing in, not
        // whichever region happens to set the PVP flag. The innermost region is the tightest box, so
        // the knockback/teleport distance stays local instead of being measured against a giant
        // world-spanning spawn region.
        ProtectedRegion bounds = this.innermostRegion(applicableRegions);
        if (bounds == null) {
            return Optional.empty();
        }

        return Optional.of(new WorldGuardRegion(location.getWorld(), bounds));
    }

    @Override
    public Collection<Region> getRegions(World world) {
        RegionManager regionManager = this.regionContainer.get(BukkitAdapter.adapt(world));
        if (regionManager == null) {
            return Collections.emptyList();
        }

        return regionManager.getRegions()
            .values()
            .stream()
            .filter(this::isCombatRegion)
            .map(region -> (Region) new WorldGuardRegion(world, region))
            .toList();
    }

    // Whether the player's current location is a no-PvP / restricted spot. Uses WorldGuard's own flag
    // resolution (queryState) so inherited and priority-overridden PVP flags are honoured. This closes
    // the holes where a child region (e.g. a shop) without its own PVP flag was treated as unprotected.
    private boolean isRestricted(ApplicableRegionSet applicableRegions) {
        StateFlag.State pvpState = this.pluginConfig.regions.preventPvpInRegions
            ? applicableRegions.queryState(null, Flags.PVP)
            : null;

        // Effective PvP is denied here (inheritance/priority aware) -> safe zone, block entry.
        if (pvpState == StateFlag.State.DENY) {
            return true;
        }

        // A region in the blockedRegions list applies here, but never treat a spot as restricted when
        // a higher-priority region explicitly allows PvP. Otherwise a large umbrella region (e.g. a
        // world-spanning "spawn" listed in blockedRegions) would also block the warzone/arenas nested
        // inside it.
        if (pvpState != StateFlag.State.ALLOW) {
            for (ProtectedRegion region : applicableRegions.getRegions()) {
                if (this.regions.contains(region.getId())) {
                    return true;
                }
            }
        }

        return false;
    }

    // Per-region check used only for border rendering, where there is no single query point.
    private boolean isCombatRegion(ProtectedRegion region) {
        if (this.regions.contains(region.getId())) {
            return true;
        }

        if (this.pluginConfig.regions.preventPvpInRegions) {
            StateFlag.State flag = region.getFlag(Flags.PVP);

            if (flag != null) {
                return flag.equals(StateFlag.State.DENY);
            }
        }

        return false;
    }

    @Nullable
    private ProtectedRegion innermostRegion(ApplicableRegionSet applicableRegions) {
        return applicableRegions.getRegions().stream()
            .max(Comparator.comparingInt(ProtectedRegion::getPriority))
            .orElse(null);
    }

}
