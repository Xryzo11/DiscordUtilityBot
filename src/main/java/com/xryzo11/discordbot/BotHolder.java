package com.xryzo11.discordbot;

import net.dv8tion.jda.api.JDA;

public class BotHolder {
    private static JDA jda;

    public static void setJDA(JDA jdaInstance) {
        jda = jdaInstance;
    }

    public static JDA getJDA() {
        return jda;
    }
}