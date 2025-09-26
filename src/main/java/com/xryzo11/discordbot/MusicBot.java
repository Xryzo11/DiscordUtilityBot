package com.xryzo11.discordbot;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MusicBot {
    public final AudioPlayerManager playerManager;
    public static AudioPlayer player;
    public static final LinkedBlockingQueue<AudioTrack> trackQueue = new LinkedBlockingQueue<>();
    public AudioTrack currentTrack;
    private boolean loopEnabled = false;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    public static boolean isCancelled = false;

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
                            File jsonSource = new File(file.getParent(), fileName.replace(".webm", ".info.json"));
                            File jsonTarget = new File(audioDir, videoId + ".info.json");
                            if (jsonSource.exists() && !jsonTarget.exists()) {
                                java.nio.file.Files.copy(jsonSource.toPath(), jsonTarget.toPath());
                            }
                        } catch (IOException e) {
                            System.out.println("[MusicBot] Failed to copy preloaded track: " + e.getMessage());
                        }
                    } else {
                        System.out.println("[MusicBot] File already exists, skipping copy: " + targetFile.getName());
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
    }

    public void playTrack(AudioTrack track) {
        if (player.getPlayingTrack() == null) {
            player.playTrack(track);
        } else {
            trackQueue.offer(track);
        }
    }

    public static void playNextTrack() {
        AudioTrack nextTrack = trackQueue.poll();
        if (nextTrack != null) {
            player.playTrack(nextTrack);
        } else {
            player.stopTrack();
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
            builder.append("▶️ **Now Playing:**\n")
                    .append(formatTrackInfo(currentTrack))
                    .append("\n\n");
        }

        if (trackQueue.isEmpty()) {
            builder.append("\uD83C\uDF10 Queue is empty");
        } else {
            builder.append("\uD83D\uDCCB **Queue:**\n");
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

    private String formatTrackInfo(AudioTrack track) {
        AudioTrackInfo info = track.getInfo();
        long durationMs = track.getDuration();

        String uri = info.uri;
        String youtubeId = uri.substring(uri.lastIndexOf('/') + 1).replace(".webm", "");

        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60;
        String durationStr = String.format("%d:%02d", minutes, seconds);

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

    public void queue(SlashCommandInteractionEvent event, String url, Guild guild, Member member) {
        event.deferReply().queue(hook -> queue(hook, url, guild, member, event));
    }

    public void queue(InteractionHook hook, String url, Guild guild, Member member, SlashCommandInteractionEvent event) {
        CompletableFuture.runAsync(() -> {
            try {
                boolean isPlaylist = url.contains("playlist?list=") || url.contains("&list=");

                if (isPlaylist) {
                    hook.editOriginal("📋 Processing playlist...").queue();
                    AudioProcessor.processYouTubePlaylist(url).thenAccept(videoUrls -> {
                        final int totalTracks = videoUrls.size();
                        final int[] addedCount = {0};
                        final List<CompletableFuture<?>> futures = Collections.synchronizedList(new ArrayList<>());

                        if (totalTracks == 0) {
                            hook.editOriginal("❌ No tracks found in playlist").queue();
                            return;
                        }

                        if (totalTracks > 250) {
                            hook.editOriginal("❗ Playlist is too long! (Max 250 tracks)").queue();
                            return;
                        }

                        AtomicBoolean wasCancelled = new AtomicBoolean(false);

                        for (int i = 0; i < totalTracks; i++) {
                            if (isCancelled) {
                                wasCancelled.set(true);
                                futures.forEach(f -> f.cancel(true));
                                futures.clear();
                                hook.editOriginal("❗ Playlist processing cancelled").queue();
                                break;
                            }

                            String videoUrl = videoUrls.get(i);
                            CompletableFuture<?> future = CompletableFuture.runAsync(() -> {
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
                                            hook.editOriginal(String.format("❌ Failed to process track: %s\n%d/%d",
                                                    e.getMessage(), addedCount[0], totalTracks)).queue();
                                        }
                                    }
                                }
                            }, executor);

                            futures.add(future);
                        }

                        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                .thenRun(() -> {
                                    if (wasCancelled.get()) {
                                        hook.editOriginal("❗ Playlist processing cancelled").queue();
                                    } else if (!isCancelled) {
                                        hook.editOriginal("✅ Playlist processing complete").queue();
                                    }
                                });

                    }).exceptionally(e -> {
                        hook.editOriginal("❌ Failed to process playlist: " + e.getMessage()).queue();
                        return null;
                    });
                } else {
                    hook.editOriginal("⏳ Processing YouTube URL...").queue();

                    String videoUrl = url;
                    if (videoUrl.contains("/shorts/")) {
                        videoUrl = videoUrl.replace("/shorts/", "/watch?v=");
                    }
                    String videoId = AudioProcessor.extractVideoId(videoUrl);
                    File audioFile = new File(AudioProcessor.AUDIO_DIR + videoId + ".webm");
                    File infoFile = new File(AudioProcessor.AUDIO_DIR + videoId + ".info.json");

                    if (audioFile.exists() && audioFile.length() > 0 && infoFile.exists()) {
                        hook.editOriginal("🔍 Fetching metadata...").queue();
                    } else {
                        hook.editOriginal("📥 Downloading audio...").queue();
                    }

                    AudioTrackInfo trackInfo = AudioProcessor.processYouTubeAudio(videoUrl, true).get();

                    if (!audioFile.exists() || !audioFile.canRead()) {
                        hook.editOriginal("❌ Audio file not ready. Please try again.").queue();
                        return;
                    }

                    if (Config.isAsmrBlockEnabled()) {
                        if (trackInfo.title.contains("ASMR") || trackInfo.title.contains("F4A") || trackInfo.title.contains("F4M") || trackInfo.title.contains("F4F") || trackInfo.title.contains("M4A") || trackInfo.title.contains("M4M") || trackInfo.title.contains("M4F")) {
                            hook.editOriginal("❌ ASMR content is blocked.").queue();
                            return;
                        }
                    }

                    hook.editOriginal("🎵 Loading track into player...").queue();

                    int retries = 0;
                    while (retries < 10) {
                        try (FileInputStream fis = new FileInputStream(audioFile)) {
                            if (audioFile.length() > 0) break;
                        } catch (Exception ignored) {}
                        Thread.sleep(200);
                        retries++;
                    }

                    playerManager.loadItem(trackInfo.uri, new AudioLoadResultHandler() {
                        @Override
                        public void trackLoaded(AudioTrack track) {
                            track.setUserData(trackInfo.title);
                            trackQueue.offer(track);

                            String message = player.getPlayingTrack() == null ?
                                    "✅ Added and playing: " + trackInfo.title :
                                    "✅ Added to queue: " + trackInfo.title;

                            hook.editOriginal(message).queue();

                            if (player.getPlayingTrack() == null) {
                                playNextTrack();
                            }
                        }

                        @Override
                        public void playlistLoaded(AudioPlaylist playlist) {}

                        @Override
                        public void noMatches() {
                            hook.editOriginal("❌ No matching audio found").queue();
                        }

                        @Override
                        public void loadFailed(FriendlyException exception) {
                            hook.editOriginal("❌ Failed to load track: " + exception.getMessage()).queue();
                        }
                    });
                }
            } catch (Exception e) {
                hook.editOriginal("❌ Error processing track: " + e.getMessage()).queue();
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

                    ProcessBuilder processBuilder = new ProcessBuilder(
                            "yt-dlp",
                            "--get-id",
                            "ytsearch1:" + query
                    );
                    Process process = processBuilder.start();

                    String videoId;
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        videoId = reader.readLine();
                        if (BotSettings.isDebug()) {
                            System.out.println("[search] Video ID: " + videoId);
                        }
                    }

                    int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        hook.editOriginal("❌ Failed to search for video").queue();
                        return;
                    }

                    if (videoId == null || videoId.isEmpty() || videoId.contains("ERROR") || videoId.trim().isEmpty()) {
                        hook.editOriginal("❌ No results found").queue();
                        return;
                    }

                    String videoUrl = "https://www.youtube.com/watch?v=" + videoId;
                    queue(event.getHook(), videoUrl, guild, member, event);

                } catch (Exception e) {
                    e.printStackTrace();
                    hook.editOriginal("❌ An error occurred while searching").queue();
                }
            });
        });
    }

    public void preload(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        String url = event.getOption("url").getAsString();
        String videoId = AudioProcessor.extractVideoId(url);
        String name = event.getOption("name").getAsString();

        CompletableFuture.runAsync(() -> {
            try {
                List<String> command = new ArrayList<>();
                command.add("yt-dlp");
                command.add("--format");
                command.add("bestaudio[ext=webm]/bestaudio");
                command.add("-o");
                command.add(Config.getPreloadedDirectory() + name + " [(" + videoId + ")]" + ".%(ext)s");
                command.add("--write-info-json");
                command.add("--force-ipv4");
                command.add("--no-check-certificate");
                if (Config.isYtCookiesEnabled()) {
                    command.add("--cookies-from-browser");
                    command.add(Config.getYtCookiesBrowser());
                }
                command.add(url);

                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.redirectErrorStream(true);
                Process process = processBuilder.start();

                if (!process.waitFor(2, TimeUnit.MINUTES)) {
                    process.destroyForcibly();
                    event.getHook().sendMessage("❌ Download timed out").queue();
                    return;
                }

                if (process.exitValue() != 0) {
                    event.getHook().sendMessage("❌ Failed to download track").queue();
                    return;
                }

                event.getHook().sendMessage("✅ Track pre-loaded as: " + name).queue();

            } catch (Exception e) {
                event.getHook().sendMessage("❌ Error: " + e.getMessage()).queue();
            }
        });
    }

    public void addPreloaded(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        event.getHook().editOriginal("⏳ Adding preloaded track...").queue();

        String name = event.getOption("track").getAsString();
        File dir = new File(Config.getPreloadedDirectory());
        File[] matchingFiles = dir.listFiles((d, f) -> f.startsWith(name + " [(") && f.endsWith(")].webm"));

        if (matchingFiles == null || matchingFiles.length == 0) {
            event.getHook().editOriginal("❌ No preloaded track found with that name").queue();
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
                File jsonSource = new File(audioFile.getParent(), name + " [(" + videoId + ")].info.json");
                File jsonTarget = new File(targetDir, videoId + ".info.json");
                if (jsonSource.exists() && !jsonTarget.exists()) {
                    java.nio.file.Files.copy(jsonSource.toPath(), jsonTarget.toPath());
                }
            } else {
                System.out.println("[addPreloaded] File already exists, skipping copy: " + targetFile.getName());
            }
        } catch (IOException e) {
            event.getHook().editOriginal("⚠️ Failed to copy preloaded track: " + e.getMessage()).queue();
            return;
        }

        queue(event.getHook(), videoUrl, event.getGuild(), event.getMember(), event);
    }

    private void loadTrack(AudioTrackInfo processedTrack, InteractionHook hook, int[] addedCount, int totalTracks) {
        if (isCancelled) {
            hook.editOriginal("❗ Playlist processing cancelled").queue();
            return;
        }

        playerManager.loadItem(processedTrack.uri, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                track.setUserData(processedTrack.title);
                if (isCancelled) {
                    hook.editOriginal("❗ Playlist processing cancelled").queue();
                    return;
                }
                trackQueue.offer(track);
                synchronized (addedCount) {
                    addedCount[0]++;
                    hook.editOriginal(String.format("✅ Queued: %s\n%d/%d",
                            processedTrack.title, addedCount[0], totalTracks)).queue();
                }
                if (player.getPlayingTrack() == null) {
                    playNextTrack();
                }
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {}

            @Override
            public void noMatches() {
                if (isCancelled) {
                    hook.editOriginal("❗ Playlist processing cancelled").queue();
                    return;
                }
                synchronized (addedCount) {
                    addedCount[0]++;
                    hook.editOriginal(String.format("❌ No matches found\n%d/%d",
                            addedCount[0], totalTracks)).queue();
                }
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                if (isCancelled) {
                    hook.editOriginal("❗ Playlist processing cancelled").queue();
                    return;
                }
                synchronized (addedCount) {
                    addedCount[0]++;
                    hook.editOriginal(String.format("❌ Failed to load track\n%d/%d",
                            addedCount[0], totalTracks)).queue();
                }
            }
        });
    }
}
