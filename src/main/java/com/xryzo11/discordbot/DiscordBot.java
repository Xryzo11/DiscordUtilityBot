package com.xryzo11.discordbot;

import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.managers.AudioManager;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DiscordBot {
    public static Package pkg = DiscordBot.class.getPackage();
    public static String artifactId = pkg.getImplementationTitle();
    public static String version = pkg.getImplementationVersion();
    public static String fullVersion = artifactId + "-" + version + "-shaded.jar";
    public static String lastRestart = Calendar.getInstance().getTime().toString();
    public static String workingDirectory = System.getProperty("user.dir");

    public static void main(String[] args) throws Exception {
        System.out.print("\n");
        System.out.println("File: " + fullVersion);
        System.out.println("Last restart: " + lastRestart);
        System.out.print("\n");
        ScriptGenerator.createNewScripts(workingDirectory + File.separator);
        if (Config.isAudioCleanupEnabled()) AudioProcessor.cleanupAudioDirectory();
        if (Config.isAutoRestartEnabled()) {
            restart(() -> {
                try {
                    tryRestart();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        MusicBot musicBot = new MusicBot();
        String token = Config.getBotToken();
        JDA jda = JDABuilder.createDefault(token)
                .addEventListeners(new WywozBindingManager.VoiceJoinListener())
                .addEventListeners(new SlashCommands.SlashCommandInteractionEventListener(musicBot))
                .build();
        BotHolder.setJDA(jda);
        jda.awaitReady();
        jda.updateCommands().queue();
        for (Guild guild : jda.getGuilds()) {
            guild.updateCommands().addCommands(
                    Commands.slash("join", "Join voice channel")
                            .setGuildOnly(true),
                    Commands.slash("play", "Queue YouTube audio")
                            .addOption(OptionType.STRING, "url", "YouTube URL", true)
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
                            .setGuildOnly(true),
                    Commands.slash("shuffle", "Shuffle the queue")
                            .setGuildOnly(true),
                    Commands.slash("playhead", "Move playhead to a specific position")
                            .addOption(OptionType.INTEGER, "hour", "Hour timestamp", true)
                            .addOption(OptionType.INTEGER, "minute", "Minute timestamp", true)
                            .addOption(OptionType.INTEGER, "second", "Seconds timestamp", true)
                            .setGuildOnly(true),
                    Commands.slash("add", "Add pre-downloaded track to the queue")
                            .addOption(OptionType.STRING, "track", "Short track name", true)
                            .setGuildOnly(true),
                    Commands.slash("rps-challenge", "Challenge a user to Rock-Paper-Scissors")
                            .addOption(OptionType.USER, "user", "User to challenge", true)
                            .setGuildOnly(true),
                    Commands.slash("rps-choose", "Rock-Paper-Scissors choice")
                            .addOption(OptionType.STRING, "choice", "rock / paper / scissors", true)
                            .setGuildOnly(true),
                    Commands.slash("rps-cancel", "Cancel the current Rock-Paper-Scissors game")
                            .setGuildOnly(true)
            ).queue();
        }
        WywozBindingManager.loadBindings();
        Dashboard.start();
    }

    public static void restart(Runnable task) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        Calendar now = Calendar.getInstance(), next = (Calendar) now.clone();
        next.set(Calendar.HOUR_OF_DAY, Config.getAutoRestartHour());
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.before(now)) next.add(Calendar.DATE, 1);
        long delay = next.getTimeInMillis() - now.getTimeInMillis();
        long day = 24 * 60 * 60 * 1000;
        scheduler.scheduleAtFixedRate(task, delay, day, TimeUnit.MILLISECONDS);
    }

    public static boolean isBeingUsed() {
        JDA jda = BotHolder.getJDA();
        if (jda != null) {
            for (Guild guild : jda.getGuilds()) {
                AudioManager audioManager = guild.getAudioManager();
                GuildChannel channel = audioManager.getConnectedChannel();
                if (channel instanceof VoiceChannel) {
                    VoiceChannel connectedChannel = (VoiceChannel) channel;
                    List<Member> members = connectedChannel.getMembers();
                    long nonBotCount = members.stream()
                            .filter(member -> !member.getUser().isBot())
                            .count();
                    if (nonBotCount > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void tryRestart() throws IOException {
        if (isBeingUsed()) {
            System.out.println("Skipping restart");
            return;
        }
        ProcessBuilder process = new ProcessBuilder("./restart.sh");
        process.start();
    }

    public static void forceRestart() throws IOException {
        ProcessBuilder process = new ProcessBuilder("./restart.sh");
        process.start();
    }
}