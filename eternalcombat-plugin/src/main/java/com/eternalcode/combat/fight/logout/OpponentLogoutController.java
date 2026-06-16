package com.eternalcode.combat.fight.logout;

import com.eternalcode.combat.fight.FightManager;
import com.eternalcode.combat.fight.FightTag;
import com.eternalcode.combat.fight.event.CauseOfUnTag;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Server;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

// Immediately release any player whose combat opponents have all logged off, since there is nobody left online to fight.
public class OpponentLogoutController implements Listener {

    private final FightManager fightManager;
    private final Server server;

    public OpponentLogoutController(FightManager fightManager, Server server) {
        this.fightManager = fightManager;
        this.server = server;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID quitter = event.getPlayer().getUniqueId();
        List<UUID> toRelease = new ArrayList<>();

        for (FightTag tag : this.fightManager.getFights()) {
            UUID player = tag.getTaggedPlayer();

            // The quitter handles their own logout; only release others who are still online.
            if (player.equals(quitter) || this.server.getPlayer(player) == null) {
                continue;
            }

            Set<UUID> opponents = this.fightManager.getOpponents(player);

            // No recorded opponents means a non-player tag (e.g. command); never auto-release those.
            if (opponents.isEmpty()) {
                continue;
            }

            // Treat the quitter as offline now, since they may still report online during their own quit event.
            boolean allOpponentsOffline = opponents.stream()
                .allMatch(opponent -> opponent.equals(quitter) || this.server.getPlayer(opponent) == null);

            if (allOpponentsOffline) {
                toRelease.add(player);
            }
        }

        toRelease.forEach(player -> this.fightManager.untag(player, CauseOfUnTag.OPPONENT_LOGOUT));
    }
}
