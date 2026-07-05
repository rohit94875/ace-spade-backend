package com.acespade.model.enums;

/**
 * What happens when a human player leaves mid-game.
 */
public enum DisconnectPolicy {
    /** Remaining player(s) win; game ends immediately. */
    FORFEIT_WIN,
    /** Leaver's seat is taken over by BOT Vitality; game continues. */
    BOT_TAKEOVER
}
