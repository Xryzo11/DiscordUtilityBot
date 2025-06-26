package com.xryzo11.discordbot;

public class BotSettings {
    private static boolean wywozSmieci = false;
    private static boolean debug = false;

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
