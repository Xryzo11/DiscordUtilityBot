package com.xryzo11.discordbot;

import com.xryzo11.discordbot.core.*;
import com.xryzo11.discordbot.listeners.*;
import com.xryzo11.discordbot.misc.WywozBindingManager;
import com.xryzo11.discordbot.musicBot.AudioProcessor;
import com.xryzo11.discordbot.musicBot.MusicBot;
import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.GatewayIntent;

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
    public static MusicBot musicBot;
    public static PresenceManager presenceManager;

    public static void main(String[] args) throws Exception {
        if (Config.isYtDlpUpdateEnabled()) {
            ProcessBuilder ytdlpUpdate = new ProcessBuilder("yt-dlp", "-U");
            ytdlpUpdate.start();
        }
        System.out.print("\n");
        System.out.println("File: " + fullVersion);
        System.out.println("Last restart: " + lastRestart);
        System.out.print("\n");
        if (Config.isConfigUpdateEnabled()) {
            Config.updateConfig();
            System.out.println("\n");
        }
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
        musicBot = new MusicBot();
        String token = Config.getBotToken();
        JDA jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                .addEventListeners(new VoiceJoinListener())
                .addEventListeners(new SlashCommandListener())
                .addEventListeners(new AutoCompleteListener())
                .addEventListeners(new ReactionListener())
                .addEventListeners(new GuildJoinListener())
                .build();
        presenceManager = new PresenceManager(jda);
        jda.addEventListener(presenceManager);
        BotHolder.setJDA(jda);
        jda.awaitReady();
        jda.updateCommands().queue();
        OptionData rpsChoices = new OptionData(OptionType.STRING, "choice", "rock / paper / scissors", true)
                .addChoice("rock", "rock")
                .addChoice("paper", "paper")
                .addChoice("scissors", "scissors");
        OptionData preloadedTracks = new OptionData(OptionType.STRING, "track", "Pre-downloaded track name", true)
                .setAutoComplete(true);
        for (Guild guild : jda.getGuilds()) {
            guild.updateCommands().addCommands(
                    Commands.slash("join", "Join voice channel"),
                    Commands.slash("play", "Queue YouTube audio by url")
                            .addOption(OptionType.STRING, "url", "YouTube URL", true),
                    Commands.slash("queue", "Queue YouTube audio by url")
                            .addOption(OptionType.STRING, "url", "YouTube URL", true),
                    Commands.slash("dequeue", "Remove a track from the queue")
                            .addOption(OptionType.INTEGER, "position", "Track position in the queue", true),
                    Commands.slash("search", "Queue YouTube audio by title")
                            .addOption(OptionType.STRING, "query", "Search query", true),
                    Commands.slash("pause", "Pause current playback"),
                    Commands.slash("resume", "Resume playback"),
                    Commands.slash("clear", "Clear the queue"),
                    Commands.slash("stop", "Stop playback and disconnect"),
                    Commands.slash("list", "List current queue"),
                    Commands.slash("skip", "Skip the current track"),
                    Commands.slash("loop", "Toggle track looping"),
                    Commands.slash("shuffle", "Shuffle the queue"),
                    Commands.slash("playhead", "Move playhead to a specific position")
                            .addOption(OptionType.INTEGER, "hour", "Hour timestamp", true)
                            .addOption(OptionType.INTEGER, "minute", "Minute timestamp", true)
                            .addOption(OptionType.INTEGER, "second", "Seconds timestamp", true),
                    Commands.slash("preload", "Pre-download a track for later use")
                            .addOption(OptionType.STRING, "url", "YouTube URL", true)
                            .addOption(OptionType.STRING, "name", "Short track name", true),
                    Commands.slash("add", "Add pre-downloaded track to the queue")
                            .addOptions(preloadedTracks),
                    Commands.slash("rps-challenge", "Challenge a user to Rock-Paper-Scissors")
                            .addOption(OptionType.USER, "user", "User to challenge", true),
                    Commands.slash("rps-choose", "Rock-Paper-Scissors choice")
                            .addOptions(rpsChoices),
                    Commands.slash("rps-cancel", "Cancel the current Rock-Paper-Scissors game"),
                    Commands.slash("roll", "Roll 'x' sided dice 'y' times (1d6 default)")
                            .addOption(OptionType.INTEGER, "sides", "Number of sides on the dice", false)
                            .addOption(OptionType.INTEGER, "times", "Number of times to roll the dice", false),
                    Commands.slash("restore", "Restore roles from the old server")
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