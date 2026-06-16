package com.eternalcode.combat.fight.logout;

import com.eternalcode.combat.fight.FightManager;
import com.eternalcode.combat.fight.FightTag;
import com.eternalcode.combat.fight.event.CauseOfUnTag;
import com.eternalcode.commons.bukkit.scheduler.MinecraftScheduler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Server;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

// Release any player whose combat opponents have all logged off, since there is nobody left online to fight.
public class OpponentLogoutController implements Listener {

    private final FightManager fightManager;
    private final Server server;
    private final MinecraftScheduler scheduler;

    public OpponentLogoutController(FightManager fightManager, Server server, MinecraftScheduler scheduler) {
        this.fightManager = fightManager;
        this.server = server;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // Defer one tick so the quitting player is fully offline before we decide who is left to fight.
        this.scheduler.runLater(this::releaseOpponentlessPlayers, Duration.ofMillis(50));
    }

    private void releaseOpponentlessPlayers() {
        List<UUID> toRelease = new ArrayList<>();

        for (FightTag tag : this.fightManager.getFights()) {
            UUID player = tag.getTaggedPlayer();

            // An offline player is not stuck in combat, and untagging one would break message handlers that expect an online player.
            if (this.server.getPlayer(player) == null) {
                continue;
            }

            Set<UUID> opponents = this.fightManager.getOpponents(player);

            // No recorded opponents means a non-player tag (e.g. command); never auto-release those.
            if (opponents.isEmpty()) {
                continue;
            }

            if (opponents.stream().allMatch(opponent -> this.server.getPlayer(opponent) == null)) {
                toRelease.add(player);
            }
        }

        toRelease.forEach(player -> this.fightManager.untag(player, CauseOfUnTag.OPPONENT_LOGOUT));
    }
}
