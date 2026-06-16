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
    // Per-combatant set of players who tagged them; cleared only when that combatant leaves combat, so a dead/logged-off opponent is still seen (as offline) by whoever they fought.
    private final Map<UUID, Set<UUID>> opponents = new ConcurrentHashMap<>();
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
            this.opponents.computeIfAbsent(target, key -> ConcurrentHashMap.newKeySet()).add(tagger);
        }

        return event;
    }

    @Override
    public Collection<FightTag> getFights() {
        return Collections.unmodifiableCollection(this.fights.values());
    }

    @Override
    public Set<UUID> getOpponents(UUID player) {
        Set<UUID> playerOpponents = this.opponents.get(player);
        return playerOpponents == null ? Collections.emptySet() : Set.copyOf(playerOpponents);
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
