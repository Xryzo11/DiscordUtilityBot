package com.xryzo11.discordbot;

import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class DiscordBot {
    public static void main(String[] args) throws Exception {
        MusicBot musicBot = new MusicBot();
        String token = Config.getBotToken();
        JDA jda = JDABuilder.createDefault(token)
                .addEventListeners(new MusicBot.VoiceJoinListener())
                .addEventListeners(new SlashCommands.SlashCommandInteractionEventListener(musicBot))
                .build();
        BotHolder.setJDA(jda);
        jda.awaitReady();
        jda.updateCommands().queue();
        for (Guild guild : jda.getGuilds()) {
            guild.updateCommands().addCommands(
                    Commands.slash("join", "Join voice channel")
                            .setGuildOnly(true),
                    Commands.slash("queue", "Queue YouTube audio")
                            .addOption(OptionType.STRING, "url", "YouTube URL", true)
                            .setGuildOnly(true),
                    Commands.slash("pause", "Pause current playback")
                            .setGuildOnly(true),
                    Commands.slash("resume", "Resume playback")
                            .setGuildOnly(true),
                    Commands.slash("clear", "Clear the queue")
                            .setGuildOnly(true),
                    Commands.slash("stop", "Stop playback and disconnect")
                            .setGuildOnly(true),
                    Commands.slash("list", "List current queue")
                            .setGuildOnly(true),
                    Commands.slash("skip", "Skip the current track")
                            .setGuildOnly(true),
                    Commands.slash("loop", "Toggle track looping")
                            .setGuildOnly(true)
            ).queue();
        }
        WywozBindingManager.loadBindings();
        Dashboard.start();
    }
}