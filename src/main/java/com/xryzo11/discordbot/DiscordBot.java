package com.xryzo11.discordbot;

import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class DiscordBot {
    public static void main(String[] args) throws Exception {
        MusicBot musicBot = new MusicBot();
//        String token = System.getenv("DISCORD_BOT_TOKEN");
        String token = "MTE0Njk1NTA2MjU0ODExOTU2Mw.GLhfzj._hnZysbjS-g-S1Qocph117_WcCgaKG9q5HdEao";
        JDA jda = JDABuilder.createDefault(token)
                .addEventListeners(new MusicBot.VoiceJoinListener())
                .addEventListeners(new SlashCommands.SlashCommandInteractionEventListener(musicBot))
                .build();
        BotHolder.setJDA(jda);
        jda.updateCommands().addCommands(
                Commands.slash("join", "Join voice channel"),
                Commands.slash("queue", "Queue YouTube audio")
                        .addOption(OptionType.STRING, "url", "YouTube URL", true),
                Commands.slash("pause", "Pause current playback"),
                Commands.slash("resume", "Resume playback"),
                Commands.slash("clear", "Clear the queue"),
                Commands.slash("stop", "Stop playback and disconnect"),
                Commands.slash("list", "List current queue"),
                Commands.slash("skip", "Skip the current track")
        ).queue();
        WywozBindingManager.loadBindings();
        Dashboard.start();
    }
}