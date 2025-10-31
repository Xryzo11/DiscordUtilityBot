package com.xryzo11.discordbot.utils.enums;

public enum PresenceStatus {
    ONLINE,
    IDLE,
    DND,
    INVISIBLE;

    @Override
    public String toString() {
        return switch (this) {
            case ONLINE -> "online";
            case IDLE -> "idle";
            case DND -> "dnd";
            case INVISIBLE -> "invisible";
        };
    }
}
