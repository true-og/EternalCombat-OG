package com.eternalcode.combat.fight;

import com.eternalcode.combat.event.EventManager;

import com.eternalcode.combat.fight.event.CauseOfTag;
import com.eternalcode.combat.fight.event.CauseOfUnTag;
import com.eternalcode.combat.fight.event.FightTagEvent;
import com.eternalcode.combat.fight.event.FightUntagEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public class FightManagerImpl implements FightManager {

    private final Map<UUID, FightTag> fights = new ConcurrentHashMap<>();
    // Per-combatant map of opponent -> when that pairing expires; entries expire with the combat tag so only current opponents count, avoiding stale links from earlier fights.
    private final Map<UUID, Map<UUID, Instant>> opponents = new ConcurrentHashMap<>();
    private final EventManager eventManager;

    public FightManagerImpl(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    @Override
    public boolean isInCombat(UUID player) {
        if (!this.fights.containsKey(player)) {
            return false;
        }

        FightTag fightTag = this.fights.get(player);

        return !fightTag.isExpired();
    }

    @Override
    public FightUntagEvent untag(UUID player, CauseOfUnTag causeOfUnTag) {
        FightUntagEvent event = this.eventManager.publishEvent(new FightUntagEvent(player, causeOfUnTag));
        if (event.isCancelled()) {
            return event;
        }

        this.fights.remove(player);
        this.opponents.remove(player);
        return event;
    }

    @Override
    public FightTagEvent tag(UUID target, Duration delay, CauseOfTag causeOfTag) {
        return this.tag(target, delay, causeOfTag, null);
    }

    @ApiStatus.Experimental
    @Override
    public FightTagEvent tag(UUID target, Duration delay, CauseOfTag causeOfTag, @Nullable UUID tagger) {
        FightTagEvent event = this.eventManager.publishEvent(new FightTagEvent(target, causeOfTag));

        if (event.isCancelled()) {
            return event;
        }
        Instant now = Instant.now();
        Instant endOfCombatLog = now.plus(delay);

        FightTag fightTag = new FightTagImpl(target, endOfCombatLog, tagger);

        this.fights.put(target, fightTag);

        if (tagger != null) {
            this.opponents.computeIfAbsent(target, key -> new ConcurrentHashMap<>()).put(tagger, endOfCombatLog);
        }

        return event;
    }

    @Override
    public Collection<FightTag> getFights() {
        return Collections.unmodifiableCollection(this.fights.values());
    }

    @Override
    public Set<UUID> getOpponents(UUID player) {
        Map<UUID, Instant> playerOpponents = this.opponents.get(player);
        if (playerOpponents == null) {
            return Collections.emptySet();
        }

        // Drop opponents whose pairing has expired so only players currently fighting this player are returned.
        Instant now = Instant.now();
        playerOpponents.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));

        return Set.copyOf(playerOpponents.keySet());
    }

    @Override
    public FightTag getTag(UUID target) {
        return this.fights.get(target);
    }

    @Override
    public void untagAll() {
        this.fights.clear();
        this.opponents.clear();
    }
}
