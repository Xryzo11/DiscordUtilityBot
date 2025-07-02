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
        WywozBindingManager.loadBindings();
        Dashboard.start();
        for (Guild guild : jda.getGuilds()) {
            guild.updateCommands().addCommands(
                    Commands.slash("join", "Join voice channel"),
                    Commands.slash("queue", "Queue YouTube audio")
                            .addOption(OptionType.STRING, "url", "YouTube URL", true),
                    Commands.slash("pause", "Pause current playback"),
                    Commands.slash("resume", "Resume playback"),
                    Commands.slash("clear", "Clear the queue"),
                    Commands.slash("stop", "Stop playback and disconnect"),
                    Commands.slash("list", "List current queue"),
                    Commands.slash("skip", "Skip the current track"),
                    Commands.slash("loop", "Toggle track looping")
            ).queue();
        }
    }
}