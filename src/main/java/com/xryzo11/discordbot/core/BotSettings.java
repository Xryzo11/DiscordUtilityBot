package com.xryzo11.discordbot.core;

public class BotSettings {
    private static boolean wywozSmieci = Config.isAutoKickEnabled();
    private static boolean wywozSmieciAuto = Config.isAutoKickAutoEnabled();
    private static final boolean wywozSmieciInitial = wywozSmieci;
    private static boolean debug = Config.isDebugEnabled();
    private static boolean tempRole = Config.isTempRoleEnabled();
    private static boolean tempRoleAuto = Config.isTempRoleAutoEnabled();
    private static final boolean tempRoleInitial = tempRole;

    public static boolean isWywozSmieci() {
        return wywozSmieci;
    }

    public static void setWywozSmieci(boolean state) {
        BotSettings.wywozSmieci = state;
        BotSettings.wywozSmieciAuto = state;
    }

    public static boolean isDebug() {
        return debug;
    }

    public static void setDebug(boolean state) {
        BotSettings.debug = state;
    }

    public static boolean isWywozSmieciAuto() {
        return wywozSmieciAuto;
    }

    public static boolean isTempRole() {
        return tempRole;
    }

    public static void setTempRole(boolean state) {
        BotSettings.tempRole = state;
        BotSettings.tempRoleAuto = state;
    }

    public static boolean isWywozSmieciInitial() {
        return wywozSmieciInitial;
    }

    public static boolean isTempRoleInitial() {
        return tempRoleInitial;
    }

    public static boolean isTempRoleAuto() {
        return tempRoleAuto;
    }
}
