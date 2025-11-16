package com.xryzo11.discordbot.musicBot;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.xryzo11.discordbot.DiscordBot;
import com.xryzo11.discordbot.core.BotHolder;
import com.xryzo11.discordbot.core.BotSettings;
import com.xryzo11.discordbot.core.Config;
import com.xryzo11.discordbot.core.SlashCommands;
import com.xryzo11.discordbot.utils.listeners.ReactionListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MusicBot {
    public final AudioPlayerManager playerManager;
    public static AudioPlayer player;
    public static final LinkedBlockingQueue<AudioTrack> trackQueue = new LinkedBlockingQueue<>();
    public AudioTrack currentTrack;
    private boolean loopEnabled = false;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    public static boolean isCancelled = false;
    public AtomicBoolean dequeueTimedOut = new AtomicBoolean(false);

    public MusicBot() {
        File audioDir = new File(Config.getAudioDirectory());
        File preloadedDir = new File(Config.getPreloadedDirectory());
        if (!audioDir.exists()) {
            audioDir.mkdirs();
        }
        if (!preloadedDir.exists()) {
            preloadedDir.mkdirs();
        }

        playerManager = new DefaultAudioPlayerManager();
        playerManager.registerSourceManager(new HttpAudioSourceManager());
        playerManager.registerSourceManager(new LocalAudioSourceManager());
        player = playerManager.createPlayer();
        playerManager.getConfiguration().setFilterHotSwapEnabled(true);

        AudioProcessor.startHttpServer();
        AudioProcessor.cleanupAudioDirectory();

        if (Config.isPreloadedCopyEnabled()) {
            for (File file : preloadedDir.listFiles()) {
                if (file.isFile() && file.getName().endsWith(".webm")) {
                    String fileName = file.getName();
                    String videoId = fileName.substring(fileName.indexOf("[(") + 2, fileName.indexOf(")].webm"));
                    File targetFile = new File(audioDir, videoId + ".webm");
                    if (!targetFile.exists()) {
                        try {
                            java.nio.file.Files.copy(
                                    file.toPath(),
                                    targetFile.toPath()
                            );
                            File jsonSource = new File(file.getParent(), fileName + ".info.json");
                            File jsonTarget = new File(audioDir, videoId + ".webm.info.json");
                            if (jsonSource.exists() && !jsonTarget.exists()) {
                                java.nio.file.Files.copy(jsonSource.toPath(), jsonTarget.toPath());
                            }
                        } catch (IOException e) {
                            if (BotSettings.isDebug()) System.out.println("[MusicBot] Failed to copy preloaded track: " + e.getMessage());
                        }
                    } else {
                        if (BotSettings.isDebug()) System.out.println("[MusicBot] File already exists, skipping copy: " + targetFile.getName());
                    }
                }
            }
        }

        player.addListener(new AudioEventAdapter() {
            @Override
            public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
                currentTrack = null;
                if (endReason.mayStartNext) {
                    if (loopEnabled && track != null) {
                        trackQueue.offer(track.makeClone());
                    }
                    MusicBot.playNextTrack();
                }
            }

            @Override
            public void onTrackStart(AudioPlayer player, AudioTrack track) {
                currentTrack = track;
            }
        });

        try {
            if (BotSettings.isDebug()) System.out.println("[MusicBot] Attempting yt-dlp update...");
            updateYtdlp();
        } catch (IOException e) {
            if (BotSettings.isDebug()) System.out.println("[MusicBot] Failed to update yt-dlp: " + e.getMessage());
        }
    }

    private static String toLocalFileUri(AudioTrackInfo info) {
        String last = info.uri.substring(info.uri.lastIndexOf('/') + 1);
        String videoId = last.replace(".webm", "");
        File audioFile = new File(AudioProcessor.AUDIO_DIR + videoId + ".webm");
        return audioFile.toURI().toString();
    }

    public static String whichYtDlp() {
        List<String> command = new ArrayList<>();

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            command.add("where");
        } else {
            command.add("which");
        }
        command.add("yt-dlp");

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Map<String, String> env = processBuilder.environment();
        String path = env.getOrDefault("PATH", System.getenv("PATH"));
        String home = System.getProperty("user.home");
        String localBin = home + "/.local/bin";
        if (!path.contains(localBin)) {
            path = localBin + ":" + path;
        }
        env.put("PATH", path);
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                if ((line = reader.readLine()) != null) {
                    return line.trim();
                }
            }
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            if (BotSettings.isDebug()) System.out.println("[whichYtDlp] Failed to locate yt-dlp: " + e.getMessage());
        }
        if (BotSettings.isDebug()) System.out.println("[whichYtDlp] Failed to locate yt-dlp. Returned null.");
        return null;
    }

    public static void setPath(ProcessBuilder processBuilder) {
        Map<String, String> env = processBuilder.environment();

        env.put("HOME", System.getProperty("user.home"));
        env.put("LANG", "en_US.UTF-8");
        env.put("LC_ALL", "en_US.UTF-8");

        String path = env.getOrDefault("PATH", System.getenv("PATH"));
        String home = System.getProperty("user.home");
        String localBin = home + "/.local/bin";
        String ffmpegBin = "/usr/bin";
        String usrLocalBin = "/usr/local/bin";
        env.put("PATH", String.join(":", List.of(localBin, ffmpegBin, usrLocalBin, path)));
        env.put("TERM", "xterm");
    }


    public static void updateYtdlp() throws IOException {
        ProcessBuilder ytdlpNightly;
        ProcessBuilder ytdlpUpdate;
        String whichResult = whichYtDlp();
        if (whichResult == null) {
            if (BotSettings.isDebug()) System.out.println("[yt-dlp update] yt-dlp not found in PATH. Skipping update.");
            return;
        } else {
            if (BotSettings.isDebug()) {
                System.out.println("[yt-dlp update] yt-dlp located at: " + whichResult);
            }
        }
        if (Config.isYtDlpUpdateEnabled()) {
            ProcessBuilder ytdlpNightlyPipx;
            ProcessBuilder ytdlpUpdatePipx;
            ytdlpNightly = new ProcessBuilder("python3", "-m", "pip", "install", "-U", "--pre", "yt-dlp[default]");
            ytdlpNightlyPipx = new ProcessBuilder("pipx", "upgrade", "yt-dlp", "--pip-args=--pre");
            ytdlpUpdate = new ProcessBuilder("pip", "install", "--upgrade", "yt-dlp");
            ytdlpUpdatePipx = new ProcessBuilder("pipx", "upgrade", "yt-dlp", "--pip-args=--pre");
            setPath(ytdlpNightly);
            setPath(ytdlpNightlyPipx);
            setPath(ytdlpUpdate);
            setPath(ytdlpUpdatePipx);
            ytdlpNightly.start();
            ytdlpNightlyPipx.start();
            ytdlpUpdate.start();
            ytdlpUpdatePipx.start();
        }
        ytdlpNightly = new ProcessBuilder(whichYtDlp(), "--update-to", "nightly");
        ytdlpUpdate = new ProcessBuilder(whichYtDlp(), "-U");
        setPath(ytdlpNightly);
        setPath(ytdlpUpdate);
        ytdlpNightly.start();
        ytdlpUpdate.start();
        List<String> command = new ArrayList<>();
        command.add(whichYtDlp());
        command.add("--version");
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        setPath(processBuilder);
        processBuilder.redirectErrorStream(true);
        Process versionProcess = processBuilder.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(versionProcess.getInputStream()))) {
            String line;
            if ((line = reader.readLine()) != null) {
                if (BotSettings.isDebug()) {
                    System.out.println("[yt-dlp update] Version: " + line);
                }
            }
        }
//        try {
//            testDownload(whichResult);
//        } catch (Exception e) {
//            if (BotSettings.isDebug()) System.out.println("[yt-dlp update] yt-dlp test download failed: " + e.getMessage());
//        }
    }

    public static void playNextTrack() {
        AudioTrack nextTrack = trackQueue.poll();
        if (nextTrack != null) {
            player.playTrack(nextTrack);
            DiscordBot.presenceManager.forcedUpdatePresence();
        } else {
            player.stopTrack();
            DiscordBot.presenceManager.forcedUpdatePresence();
        }
    }

    public void pausePlayer() {
        player.setPaused(true);
    }

    public void resumePlayer() {
        player.setPaused(false);
    }

    public void clearQueue() {
        trackQueue.clear();
    }

    public void stopPlayer() {
        player.stopTrack();
        clearQueue();
        disconnectFromAllVoiceChannels();
    }

    static void disconnectFromAllVoiceChannels() {
        JDA jda = BotHolder.getJDA();
        if (jda != null) {
            for (Guild guild : jda.getGuilds()) {
                if (guild.getAudioManager().isConnected()) {
                    guild.getAudioManager().closeAudioConnection();
                }
            }
        }
    }

    private String formatTime(long input) {
        long hours = (long) TimeUnit.SECONDS.toHours(input);
        long minutes = (long) (TimeUnit.SECONDS.toMinutes(input) % 60);
        long seconds = (long) (TimeUnit.SECONDS.toSeconds(input) % 60);

        return hours > 0
                ? String.format("%02d:%02d:%02d", hours, minutes, seconds)
                : String.format("%02d:%02d", minutes, seconds);
    }

    private String totalQueueDuration() {
        long totalDuration = 0;
        for (AudioTrack track : trackQueue) {
            long trackDuration = track.getDuration();
            if (trackDuration > 0 && trackDuration < TimeUnit.HOURS.toMillis(50)) {
                totalDuration += trackDuration / 1000;
            } else {
                totalDuration += 0;
            }
        }
        return formatTime(totalDuration);
    }

    public String getQueueList() {
        StringBuilder builder = new StringBuilder();

        builder.append("\uD83D\uDD01 **Looping:** ");
        if (loopEnabled) {
            builder.append("✅");
        } else {
            builder.append("❌");
        }
        builder.append("\n\n");

        if (currentTrack != null) {
            builder.append("▶️ **Now Playing:** ")
                    .append("[")
                    .append(formatTime(currentTrack.getPosition() / 1000))
                    .append(" / ")
                    .append(formatTime(currentTrack.getDuration() / 1000))
                    .append("]\n")
                    .append(formatTrackInfo(currentTrack))
                    .append("\n\n");
        }

        if (trackQueue.isEmpty()) {
            builder.append("\uD83C\uDF10 Queue is empty");
        } else {
            builder.append("\uD83D\uDCCB **Queue:**\n");
            builder.append("Length: ")
                    .append(trackQueue.size())
                    .append(" tracks | Total Duration: ")
                    .append(totalQueueDuration())
                    .append("\n\n");
            int index = 1;
            for (AudioTrack track : trackQueue) {
                builder.append(index++)
                        .append(". ")
                        .append(formatTrackInfo(track))
                        .append("\n");

                if (index > 10) {
                    builder.append("\n... and ")
                            .append(trackQueue.size() - 10)
                            .append(" more tracks");
                    break;
                }
            }
        }

        return builder.toString();
    }

    public void skipCurrentTrack() {
        if (player.getPlayingTrack() != null) {
            player.stopTrack();
            playNextTrack();
        }
    }

    public static String formatTrackInfo(AudioTrack track) {
        AudioTrackInfo info = track.getInfo();
        long durationMs = track.getDuration();

        String uri = info.uri;
        String youtubeId = uri.substring(uri.lastIndexOf('/') + 1).replace(".webm", "");

        String durationStr;
        if (durationMs < 0 || durationMs >= TimeUnit.HOURS.toMillis(50)) {
            durationStr = "Unknown";
        } else {
            long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs);
            long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60;
            durationStr = String.format("%d:%02d", minutes, seconds);
        }

        String youtubeUrl = "https://www.youtube.com/watch?v=" + youtubeId;
        String title = track.getUserData() != null ? track.getUserData().toString() : youtubeId;

        return String.format("[%s](<%s>) `[%s]`",
                title,
                youtubeUrl,
                durationStr);
    }

    public boolean isLoopEnabled() {
        return loopEnabled;
    }

    public void toggleLoop() {
        loopEnabled = !loopEnabled;
    }

    public void disableLoop() {
        loopEnabled = false;
    }

    public int movePlayhead(int position) {
        if (player.getPlayingTrack() != null) {
            player.getPlayingTrack().setPosition(position);
            return 0;
        }
        return 1;
    }

    public void shuffleQueue() {
        List<AudioTrack> trackList = new ArrayList<>(trackQueue);
        Collections.shuffle(trackList);
        trackQueue.clear();
        trackQueue.addAll(trackList);
    }

    public void queue(SlashCommandInteractionEvent event, String url) {
        SlashCommands.safeDefer(event);
        queue(event.getHook(), url, false);
    }

    public void queueSilent(InteractionHook hook, String url) {
        queue(hook, url, true);
    }

    public void queue(InteractionHook hook, String url, boolean silent) {
        CompletableFuture.runAsync(() -> {
            try {
                boolean isPlaylist = url.contains("playlist?list=") || url.contains("&list=");

                if (isPlaylist) {
                    if (BotSettings.isDebug()) System.out.println("[queue] Processing playlist URL: " + url);
                    if (!silent) hook.editOriginal("📋 Processing playlist...").queue();
                    AudioProcessor.processYouTubePlaylist(url).thenAccept(videoUrls -> {
                        final int totalTracks = videoUrls.size();
                        final int[] addedCount = {0};
                        final List<CompletableFuture<?>> futures = Collections.synchronizedList(new ArrayList<>());

                        if (totalTracks == 0) {
                            if (!silent) hook.editOriginal("❌ No tracks found in playlist").queue();
                            if (BotSettings.isDebug()) System.out.println("[queue] No tracks found in playlist");
                            return;
                        }

                        if (Config.isLimitPlaylistEnabled()) {
                            if (totalTracks > 250) {
                                if (!silent) hook.editOriginal("❗ Playlist has " + totalTracks + " tracks, which exceeds the limit of 250.").queue();
                                if (BotSettings.isDebug()) System.out.println("[queue] Playlist too long: " + totalTracks + " tracks");
                                return;
                            }
                        }

                        AtomicBoolean wasCancelled = new AtomicBoolean(false);

                        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
                        ScheduledFuture<?> heartbeatTask = heartbeat.scheduleAtFixedRate(() -> {
                            if (!wasCancelled.get()) {
                                hook.editOriginal(String.format(
                                        "⏳ Processing playlist: %d/%d tracks added...",
                                        addedCount[0],
                                        totalTracks
                                )).queue();
                            }
                        }, 3, 3, TimeUnit.MINUTES);

                        for (int i = 0; i < totalTracks; i++) {
                            if (isCancelled) {
                                wasCancelled.set(true);
                                futures.forEach(f -> f.cancel(true));
                                futures.clear();
                                if (!silent) hook.editOriginal("❗ Playlist processing cancelled").queue();
                                if (BotSettings.isDebug()) System.out.println("[queue] Playlist processing cancelled");
                                break;
                            }

                            String videoUrl = videoUrls.get(i);
                            CompletableFuture<?> future = CompletableFuture.runAsync(() -> {
                                if (isCancelled) {
                                    wasCancelled.set(true);
                                    return;
                                }

                                try {
                                    if (!isCancelled) {
                                        AudioTrackInfo processedTrack = AudioProcessor.processYouTubeAudio(videoUrl, true).get();
                                        if (!isCancelled) {
                                            loadTrack(processedTrack, hook, addedCount, totalTracks);
                                        }
                                    }
                                } catch (Exception e) {
                                    if (!isCancelled) {
                                        synchronized (addedCount) {
                                            addedCount[0]++;
                                            if (!silent) hook.editOriginal(String.format("❌ Failed to process track: %s\n%d/%d",
                                                    e.getMessage(), addedCount[0], totalTracks)).queue();
                                            if (BotSettings.isDebug()) System.out.println("[queue] Failed to process track: " + e.getMessage());
                                        }
                                    }
                                }
                            }, executor);

                            futures.add(future);
                        }

                        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                .whenComplete((v, ex) -> {
                                    if (wasCancelled.get()) {
                                        if (!silent) hook.editOriginal("❗ Playlist processing cancelled").queue();
                                        if (BotSettings.isDebug()) System.out.println("[queue] Playlist processing cancelled");
                                    } else if (!isCancelled) {
                                        if (!silent) hook.editOriginal("✅ Playlist processing complete").queueAfter(5, TimeUnit.SECONDS);
                                        if (BotSettings.isDebug()) System.out.println("[queue] Playlist processing complete");
                                    }
                                    heartbeatTask.cancel(false);
                                    heartbeat.shutdown();
                                });

                    }).exceptionally(e -> {
                        if (!silent) hook.editOriginal("❌ Failed to process playlist: " + e.getMessage()).queue();
                        if (BotSettings.isDebug()) System.out.println("[queue] Failed to process playlist: " + e.getMessage());
                        return null;
                    });
                } else {
                    if (!silent) hook.editOriginal("⏳ Processing YouTube URL...").queue();

                    String videoUrl = url;
                    if (videoUrl.contains("/shorts/")) {
                        videoUrl = videoUrl.replace("/shorts/", "/watch?v=");
                    }
                    String videoId = AudioProcessor.extractVideoId(videoUrl);
                    File audioFile = new File(AudioProcessor.AUDIO_DIR + videoId + ".webm");
                    File infoFile = new File(AudioProcessor.AUDIO_DIR + videoId + ".webm.info.json");

                    if (BotSettings.isDebug()) System.out.println("[queue] Attempting to queue video ID: " + videoId);

                    if (audioFile.exists() && audioFile.length() > 0 && infoFile.exists()) {
                        if (!silent) hook.editOriginal("🔍 Fetching metadata...").queue();
                    } else {
                        if (!silent) hook.editOriginal("📥 Downloading audio...").queue();
                    }

                    AudioTrackInfo trackInfo = AudioProcessor.processYouTubeAudio(videoUrl, true).get();

                    if (!audioFile.exists() || !audioFile.canRead()) {
                        if (!silent) hook.editOriginal("❌ Audio file not ready. Please try again.").queue();
                        return;
                    }

                    if (Config.isAsmrBlockEnabled()) {
                        if (trackInfo.title.contains("ASMR") || trackInfo.title.contains("F4A") || trackInfo.title.contains("F4M") || trackInfo.title.contains("F4F") || trackInfo.title.contains("M4A") || trackInfo.title.contains("M4M") || trackInfo.title.contains("M4F")) {
                            if (!silent) hook.editOriginal("❌ ASMR content is blocked.").queue();
                            return;
                        }
                    }

                    if (!silent) hook.editOriginal("🎵 Loading track into player...").queue();

                    int retries = 0;
                    while (retries < 10) {
                        try (FileInputStream fis = new FileInputStream(audioFile)) {
                            if (audioFile.length() > 0) break;
                        } catch (Exception ignored) {}
                        Thread.sleep(200);
                        retries++;
                    }

                    if (MusicBot.isCancelled) {
                        if (!silent) hook.editOriginal("❗ Track processing cancelled").queue();
                        if (BotSettings.isDebug()) System.out.println("[queue] Track processing cancelled");
                        return;
                    }

                    String filePath = audioFile.getAbsolutePath();
                    playerManager.loadItem(filePath, new AudioLoadResultHandler() {
                        @Override
                        public void trackLoaded(AudioTrack track) {
                            track.setUserData(trackInfo.title);
                            trackQueue.offer(track);
                            String message = player.getPlayingTrack() == null
                                    ? "✅ Added and playing: " + trackInfo.title
                                    : "✅ Added to queue: " + trackInfo.title;
                            if (!silent) hook.editOriginal(message).queue();
                            if (BotSettings.isDebug()) System.out.println("[queue] Queued track: " + trackInfo.title);
                            if (player.getPlayingTrack() == null) {
                                playNextTrack();
                            }
                        }
                        @Override
                        public void playlistLoaded(AudioPlaylist playlist) {}
                        @Override
                        public void noMatches() {
                            if (!silent) hook.editOriginal("❌ No matching audio found").queue();
                            if (BotSettings.isDebug()) System.out.println("[queue] No matches found for ID: " + videoId);
                        }
                        @Override
                        public void loadFailed(FriendlyException exception) {
                            if (!silent) hook.editOriginal("❌ Failed to load track: " + exception.getMessage()).queue();
                            if (BotSettings.isDebug()) System.out.println("[queue] Failed to load track: " + exception.getMessage());
                        }
                    });
                }
            } catch (Exception e) {
                if (!silent) hook.editOriginal("❌ Error processing track: " + e.getMessage()).queue();
                if (BotSettings.isDebug()) System.out.println("[queue] Error processing track: " + e.getMessage());
                if (BotSettings.isDebug()) {
                    e.printStackTrace();
                }
            }
        }, executor);
    }

    public void search(SlashCommandInteractionEvent event, String query, Guild guild, Member member) {
        event.deferReply().queue(hook -> {
            CompletableFuture.runAsync(() -> {
                try {
                    hook.editOriginal("⏳ Processing YouTube search...").queue();
                    if (BotSettings.isDebug()) System.out.println("[search] Searching for query: " + query);

                    List<String> command = new ArrayList<>();
                    command.add(whichYtDlp());
                    command.add("--get-id");
                    command.add("--ignore-errors");
                    command.add("--no-warnings");
                    command.add("-q");
                    command.add("ytsearch1:" + query);
                    if (Config.isYtCookiesEnabled()) {
                        switch (Config.getYtCookiesSource()) {
                            case "file" -> command.add("--cookies");
                            case "browser" -> command.add("--cookies-from-browser");
                        }
                        command.add(Config.getYtCookies());
                    }
                    command.add("--remote-components");
                    command.add("ejs:github");

                    ProcessBuilder processBuilder = new ProcessBuilder(command);
                    setPath(processBuilder);

                    processBuilder.redirectErrorStream(true);
                    Process process = processBuilder.start();

                    String videoId = null;
                    Pattern errorPattern = Pattern.compile("ERROR: \\[youtube\\] ([^:]+):");
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (BotSettings.isDebug()) System.out.println("[yt-dlp] " + line);

                            if (line.contains("Deprecated Feature") || line.contains("WARNING") || line.isBlank()) {
                                continue;
                            }

                            Matcher matcher = errorPattern.matcher(line);
                            if (matcher.find()) {
                                videoId = matcher.group(1);
                                break;
                            }

                            if (line.matches("^[a-zA-Z0-9_-]{11}$")) {
                                videoId = line.trim();
                                break;
                            }
                        }
                        if (BotSettings.isDebug()) System.out.println("[search] Video ID: " + videoId);
                    }

                    int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        updateYtdlp();
                        hook.editOriginal("❌ Failed to search for video").queue();
                        if (BotSettings.isDebug()) System.out.println("[search] yt-dlp exited with code: " + exitCode);
                        return;
                    }

                    if (videoId == null || videoId.isEmpty() || videoId.contains("ERROR") || videoId.trim().isEmpty()) {
                        updateYtdlp();
                        hook.editOriginal("❌ No results found").queue();
                        if (BotSettings.isDebug()) System.out.println("[search] No video ID found in search results");
                        return;
                    }

                    String videoUrl = "https://www.youtube.com/watch?v=" + videoId;
                    queue(event.getHook(), videoUrl, false);
                } catch (Exception e) {
                    e.printStackTrace();
                    try {
                        updateYtdlp();
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                    hook.editOriginal("❌ An error occurred while searching").queue();
                    if (BotSettings.isDebug()) System.out.println("[search] Error during search: " + e.getMessage());
                }
            });
        });
    }

    private static String resolveExecutable(String... candidates) {
        for (String c : candidates) {
            if (c != null && new File(c).canExecute()) return c;
        }
        return null;
    }

    public void preload(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        String url = event.getOption("url").getAsString();
        String videoId = AudioProcessor.extractVideoId(url);
        String name = event.getOption("name").getAsString();

        if (BotSettings.isDebug()) System.out.println("[preload] Preloading URL: " + url + " with name: " + name);

        CompletableFuture.runAsync(() -> {
            try {
                List<String> command = new ArrayList<>();
                command.add(whichYtDlp());
                command.add("-f");
                command.add("249/bestaudio/best");
                command.add("--audio-format"); command.add("opus");
                command.add("-o");
                command.add(Config.getPreloadedDirectory() + name + " [(" + videoId + ")]" + ".webm");
                command.add("--write-info-json");
                command.add("--force-ipv4");
                command.add("--no-check-certificate");
                command.add("--geo-bypass");
                if (Config.isYtCookiesEnabled()) {
                    switch (Config.getYtCookiesSource()) {
                        case "file" -> command.add("--cookies");
                        case "browser" -> command.add("--cookies-from-browser");
                    }
                    command.add(Config.getYtCookies());
                }
                command.add("--extractor-args");
                command.add("youtube:player_client=android,web");
                command.add("--extractor-args");
                command.add("youtube:player_skip=configs,webpage");
                command.add(url);
                command.add("--remote-components ejs:github");

                ProcessBuilder processBuilder = new ProcessBuilder(command);
                setPath(processBuilder);
                processBuilder.redirectErrorStream(true);
                Process process = processBuilder.start();

                if (!process.waitFor(2, TimeUnit.MINUTES)) {
                    process.destroyForcibly();
                    event.getHook().sendMessage("❌ Download timed out").queue();
                    if (BotSettings.isDebug()) System.out.println("[preload] Download timed out for URL: " + url);
                    return;
                }

                if (process.exitValue() != 0) {
                    event.getHook().sendMessage("❌ Failed to download track").queue();
                    if (BotSettings.isDebug()) System.out.println("[preload] yt-dlp exited with code: " + process.exitValue());
                    return;
                }

                event.getHook().sendMessage("✅ Track pre-loaded as: " + name).queue();
                if (BotSettings.isDebug()) System.out.println("[preload] Successfully preloaded track: " + name);

            } catch (Exception e) {
                event.getHook().sendMessage("❌ Error: " + e.getMessage()).queue();
                if (BotSettings.isDebug()) System.out.println("[preload] Error during preload: " + e.getMessage());
            }
        });
    }

    public void addPreloaded(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        event.getHook().editOriginal("⏳ Adding preloaded track...").queue();
        if (BotSettings.isDebug()) System.out.println("[addPreloaded] Adding preloaded track...");

        String name = event.getOption("track").getAsString();
        File dir = new File(Config.getPreloadedDirectory());
        File[] matchingFiles = dir.listFiles((d, f) -> f.startsWith(name + " [(") && f.endsWith(")].webm"));

        if (matchingFiles == null || matchingFiles.length == 0) {
            event.getHook().editOriginal("❌ No preloaded track found with that name").queue();
            if (BotSettings.isDebug()) System.out.println("[addPreloaded] No matching preloaded track found for name: " + name);
            return;
        }

        File audioFile = matchingFiles[0];
        String fileName = audioFile.getName();
        String videoId = fileName.substring(fileName.indexOf("[(") + 2, fileName.indexOf(")].webm"));
        String videoUrl = "https://www.youtube.com/watch?v=" + videoId;

        try {
            File targetDir = new File(Config.getAudioDirectory());
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }
            File targetFile = new File(targetDir, videoId + ".webm");
            if (!targetFile.exists()) {
                java.nio.file.Files.copy(
                        audioFile.toPath(),
                        targetFile.toPath()
                );
                File jsonSource = new File(audioFile.getParent(), name + " [(" + videoId + ")].webm.info.json");
                File jsonTarget = new File(targetDir, videoId + ".webm.info.json");
                if (jsonSource.exists() && !jsonTarget.exists()) {
                    java.nio.file.Files.copy(jsonSource.toPath(), jsonTarget.toPath());
                }
            } else {
                System.out.println("[addPreloaded] File already exists, skipping copy: " + targetFile.getName());
            }
        } catch (IOException e) {
            event.getHook().editOriginal("⚠️ Failed to copy preloaded track: " + e.getMessage()).queue();
            if (BotSettings.isDebug()) System.out.println("[addPreloaded] Failed to copy preloaded track: " + e.getMessage());
            return;
        }

        queue(event.getHook(), videoUrl, false);
    }

    private static String toLocalFilePath(AudioTrackInfo info) {
        String last = info.uri.substring(info.uri.lastIndexOf('/') + 1);
        String videoId = last.replace(".webm", "");
        File audioFile = new File(AudioProcessor.AUDIO_DIR + videoId + ".webm");
        return audioFile.getAbsolutePath();
    }

    private void loadTrack(AudioTrackInfo processedTrack, InteractionHook hook, int[] addedCount, int totalTracks) {
        if (isCancelled) {
            hook.editOriginal("❗ Playlist processing cancelled").queue();
            if (BotSettings.isDebug()) System.out.println("[loadTrack] Playlist processing cancelled");
            return;
        }
        String filePath = toLocalFilePath(processedTrack);
        playerManager.loadItem(filePath, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                track.setUserData(processedTrack.title);
                if (isCancelled) {
                    hook.editOriginal("❗ Playlist processing cancelled").queue();
                    if (BotSettings.isDebug()) System.out.println("[trackLoaded] Playlist processing cancelled");
                    return;
                }
                trackQueue.offer(track);
                synchronized (addedCount) {
                    addedCount[0]++;
                    hook.editOriginal(String.format("✅ Queued: %s\n%d/%d", processedTrack.title, addedCount[0], totalTracks)).queue();
                    if (BotSettings.isDebug()) System.out.println("[loadTrack] Queued track: " + processedTrack.title);
                }
                if (player.getPlayingTrack() == null) {
                    playNextTrack();
                }
            }
            @Override public void playlistLoaded(AudioPlaylist playlist) {}
            @Override public void noMatches() {
                if (isCancelled) {
                    hook.editOriginal("❗ Playlist processing cancelled").queue();
                    if (BotSettings.isDebug()) System.out.println("[noMatches] Playlist processing cancelled");
                    return;
                }
                synchronized (addedCount) {
                    addedCount[0]++;
                    hook.editOriginal(String.format("❌ No matches found\n%d/%d", addedCount[0], totalTracks)).queue();
                    if (BotSettings.isDebug()) System.out.println("[loadTrack] No matches found for track: " + processedTrack.title);
                }
            }
            @Override public void loadFailed(FriendlyException exception) {
                if (isCancelled) {
                    hook.editOriginal("❗ Playlist processing cancelled").queue();
                    if (BotSettings.isDebug()) System.out.println("[loadFailed] Playlist processing cancelled");
                    return;
                }
                synchronized (addedCount) {
                    addedCount[0]++;
                    hook.editOriginal(String.format("❌ Failed to load track\n%d/%d", addedCount[0], totalTracks)).queue();
                    if (BotSettings.isDebug()) System.out.println("[loadTrack] Failed to load track: " + exception.getMessage());
                }
            }
        });
    }

    public void dequeueTrack(SlashCommandInteractionEvent event, int position) {
        event.deferReply().queue();
        event.getHook().editOriginal("⏳ Finding track in queue...").queue();
        if (BotSettings.isDebug()) System.out.println("[dequeueTrack] Attempting to dequeue track at position: " + position);

        AudioTrack toRemove = null;
        Iterator<AudioTrack> queueIterator = trackQueue.iterator();
        for (int i = 1; queueIterator.hasNext(); i++) {
            AudioTrack track = queueIterator.next();
            if (i == position) {
                toRemove = track;
                if (BotSettings.isDebug()) System.out.println("[dequeueTrack] Found track to remove: " + formatTrackInfo(toRemove));
                break;
            }
        }

        if (toRemove != null) {
            dequeueTimedOut = new AtomicBoolean(false);
            if (ReactionListener.awaitingConfirmation) {
                event.getHook().editOriginal("❗ Another operation is already pending confirmation. Please wait.").queue();
                if (BotSettings.isDebug()) System.out.println("[dequeueTrack] Another operation is already pending.");
                return;
            }
            event.getHook().editOriginal("⚠️ Found track: " + formatTrackInfo(toRemove) + "\nReact with :white_check_mark: within 60 seconds to confirm removal.").queue(message -> {
                message.addReaction(Emoji.fromUnicode("✅")).queue();
            });
            ReactionListener.awaitingConfirmation = true;
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            ScheduledFuture<?> dequeueTimeOut = scheduler.schedule(() -> {
                dequeueTimedOut.set(true);
                ReactionListener.awaitingConfirmation = false;
                ReactionListener.confirmed = false;
                event.getHook().editOriginal("❗ Dequeue timed out. Track was not removed.").queue();
                if (BotSettings.isDebug()) System.out.println("[dequeueTrack] Dequeue timed out.");
            }, 1, TimeUnit.MINUTES);
            while (!ReactionListener.confirmed && !dequeueTimedOut.get()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    if (BotSettings.isDebug()) System.out.println("[dequeueTrack] Interrupted while waiting for confirmation: " + e.getMessage());
                }
            }
            scheduler.shutdownNow();
            ReactionListener.awaitingConfirmation = false;
            if (dequeueTimedOut.get()) {
                return;
            }
            if (!trackQueue.contains(toRemove)) {
                event.getHook().editOriginal("❗ Track was already removed from the queue.").queue();
                if (BotSettings.isDebug()) System.out.println("[dequeueTrack] Track was already removed from the queue.");
                return;
            }
            event.getHook().editOriginal("✅ Removed track from queue: " + formatTrackInfo(toRemove)).queue();
            if (BotSettings.isDebug()) System.out.println("[dequeueTrack] Successfully removed track: " + formatTrackInfo(toRemove));
            trackQueue.remove(toRemove);
        } else {
            event.getHook().editOriginal("❌ Invalid position").queue();
            if (BotSettings.isDebug()) System.out.println("[dequeueTrack] Invalid position: " + position);
        }
    }

    private static void testDownload(String whichResult) throws IOException, InterruptedException {
            ProcessBuilder test = new ProcessBuilder(
                whichResult,
                "-f", "251/250/249",
                "--audio-quality", "0",
                "--no-restrict-filenames",
                "-o", "/home/DiscordBot/discord_audio/YJdCpltq-_k.webm",
                "--write-info-json",
                "--print-json",
                "--quiet",
                "--no-warnings",
                "--force-ipv4",
                "--no-check-certificate",
                "--geo-bypass",
                "--cookies", "/home/serwer/DiscordBot/config/cookies.txt",
                "--extractor-args", "youtube:player_client=tv",
                "--extractor-args", "youtube:player_skip=configs,webpage",
                "https://www.youtube.com/watch?v=YJdCpltq-_k",
                "--remote-components", "ejs:github"
        );
        setPath(test);
        test.redirectErrorStream(true);
        Process testProcess = test.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(testProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (BotSettings.isDebug()) {
                    System.out.println("[yt-dlp] " + line);
                }
            }
        }
        ProcessBuilder test2 = new ProcessBuilder(
                whichResult,
                "--list-formats",
                "-f", "251/250/249",
                "--audio-quality", "0",
                "--no-restrict-filenames",
                "-o", "/home/DiscordBot/discord_audio/YJdCpltq-_k.webm",
                "--quiet",
                "--no-warnings",
                "--force-ipv4",
                "--no-check-certificate",
                "--geo-bypass",
                "--cookies", "/home/serwer/DiscordBot/config/cookies.txt",
                "--extractor-args", "youtube:player_client=tv",
                "--extractor-args", "youtube:player_skip=configs,webpage",
                "https://www.youtube.com/watch?v=YJdCpltq-_k",
                "--remote-components", "ejs:github"
        );
        setPath(test2);
        test2.redirectErrorStream(true);
        Process testProcess2 = test2.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(testProcess2.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (BotSettings.isDebug()) {
                    System.out.println("[yt-dlp] " + line);
                }
            }
        }
    }
}
