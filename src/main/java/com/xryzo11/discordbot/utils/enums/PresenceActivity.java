package com.xryzo11.discordbot.utils.enums;

public enum PresenceActivity {
    PLAYING,
    LISTENING,
    WATCHING,
    STREAMING;

    @Override
    public String toString() {
        return switch (this) {
            case PLAYING -> "Playing";
            case LISTENING -> "Listening to";
            case WATCHING -> "Watching";
            case STREAMING -> "Streaming";
        };
    }
}
