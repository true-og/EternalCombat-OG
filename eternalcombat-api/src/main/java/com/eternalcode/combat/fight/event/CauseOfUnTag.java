package com.eternalcode.combat.fight.event;

public enum CauseOfUnTag {
    /**
     * The combat tag expired after a set duration.
     */
    TIME_EXPIRED,

    /**
     * The player died, which resulted in the removal of the combat tag.
     */
    DEATH,

    /**
     * The player was killed by another player, causing the combat tag to be removed.
     */
    DEATH_BY_PLAYER,

    /**
     * The player logged out, triggering the removal of the combat tag.
     */
    LOGOUT,

    /**
     * A command was executed to remove the player's combat tag.
     */
    COMMAND,

    /**
     * The attacker released the player, ending the combat and removing the tag.
     */
    ATTACKER_RELEASE,

    /**
     * All of the player's combat opponents logged out, so there was nobody left to fight.
     */
    OPPONENT_LOGOUT,

    /**
     * A custom cause, typically defined by external plugins or systems.
     */
    CUSTOM
}
