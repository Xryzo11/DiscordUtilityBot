package com.xryzo11.discordbot;

public class BotSettings {
    private static boolean wywozSmieci = Config.isAutoKickEnabled();
    private static boolean debug = Config.isDebugEnabled();

    public static boolean isWywozSmieci() {
        return wywozSmieci;
    }

    public static void setWywozSmieci(boolean state) {
        BotSettings.wywozSmieci = state;
    }

    public static boolean isDebug() {
        return debug;
    }

    public static void setDebug(boolean state) {
        BotSettings.debug = state;
    }
}
