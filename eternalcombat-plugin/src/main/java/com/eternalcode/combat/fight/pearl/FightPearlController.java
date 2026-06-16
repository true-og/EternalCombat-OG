package com.eternalcode.combat.fight.pearl;

import com.eternalcode.combat.fight.FightManager;
import com.eternalcode.combat.notification.NoticeService;
import com.eternalcode.combat.util.DurationUtil;
import java.time.Duration;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class FightPearlController implements Listener {

    private final FightPearlSettings settings;
    private final NoticeService noticeService;
    private final FightManager fightManager;
    private final FightPearlService fightPearlService;

    public FightPearlController(
        FightPearlSettings settings,
        NoticeService noticeService,
        FightManager fightManager,
        FightPearlService fightPearlService
    ) {
        this.settings = settings;
        this.noticeService = noticeService;
        this.fightManager = fightManager;
        this.fightPearlService = fightPearlService;
    }

    // Block at interact via setUseItemInHand(DENY): stops the throw without consuming the pearl or desyncing the client, and never marks the delay so it can't self-cancel the first throw.
    // No ignoreCancelled: RIGHT_CLICK_AIR is cancelled-by-default in Bukkit, so skipping cancelled events would miss the common throw path and bypass the cooldown.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPearlInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.ENDER_PEARL) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!this.fightManager.isInCombat(playerId)) {
            return;
        }

        if (this.settings.pearlThrowDisabledDuringCombat) {
            event.setUseItemInHand(Event.Result.DENY);
            this.noticeService.create()
                .player(playerId)
                .notice(this.settings.pearlThrowBlockedDuringCombat)
                .send();
            return;
        }

        if (!this.settings.pearlCooldownEnabled || this.settings.pearlThrowDelay.isZero()) {
            return;
        }

        if (this.fightPearlService.hasDelay(playerId)) {
            event.setUseItemInHand(Event.Result.DENY);
            Duration remainingDelay = this.fightPearlService.getRemainingDelay(playerId);

            this.noticeService.create()
                .player(playerId)
                .notice(this.settings.pearlThrowBlockedDelayDuringCombat)
                .placeholder("{TIME}", DurationUtil.format(remainingDelay))
                .send();
        }
    }

    // Start the cooldown only once a pearl actually launches, so the timer reflects successful throws.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPearlThrow(ProjectileLaunchEvent event) {
        if (!this.settings.pearlCooldownEnabled || this.settings.pearlThrowDelay.isZero()) {
            return;
        }

        if (!(event.getEntity() instanceof EnderPearl)) {
            return;
        }

        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }

        UUID playerId = player.getUniqueId();

        if (!this.fightManager.isInCombat(playerId)) {
            return;
        }

        this.fightPearlService.markDelay(playerId);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPearlDamage(EntityDamageByEntityEvent event) {
        if (this.settings.pearlThrowDamageEnabled) {
            return;
        }

        if (!(event.getEntity() instanceof Player) ||
            !(event.getDamager() instanceof EnderPearl) ||
            event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }

        event.setDamage(0.0);
    }
}
