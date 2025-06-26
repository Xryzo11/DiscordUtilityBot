package com.xryzo11.discordbot;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import static spark.Spark.*;

public class Dashboard {
    public static void start() {
        port(21379);
        staticFiles.location("/public");

        get("/wywoz-status", (req, res) -> {
            return BotSettings.isWywozSmieci() ? "enabled" : "disabled";
        });

        post("/toggle-wywoz", (req, res) -> {
            String status = req.queryParams("status"); // expect "true" or "false"
            boolean state = Boolean.parseBoolean(status);
            BotSettings.setWywozSmieci(state);
            System.out.println("Wywoz smieci toggled to: " + (state ? "ON" : "OFF"));
            return "Wywoz smieci toggled to: " + (state ? "ON" : "OFF");
        });
    }
}
