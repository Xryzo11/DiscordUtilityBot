package com.xryzo11.discordbot.musicBot;

import com.github.topi314.lavasrc.spotify.SpotifySourceManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.xryzo11.discordbot.core.Config;
import com.xryzo11.discordbot.utils.ConfirmationHandler;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import com.xryzo11.discordbot.DiscordBot;
import com.xryzo11.discordbot.core.BotHolder;
import com.xryzo11.discordbot.core.BotSettings;
import com.xryzo11.discordbot.core.SlashCommands;
import com.xryzo11.discordbot.utils.listeners.ReactionListener;
import dev.lavalink.youtube.YoutubeSourceOptions;
import dev.lavalink.youtube.clients.*;
import dev.lavalink.youtube.clients.skeleton.Client;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MusicBot {
    public final AudioPlayerManager playerManager;
    public static AudioPlayer player;
    public static final LinkedBlockingDeque<AudioTrack> trackQueue = new LinkedBlockingDeque<>();
    public AudioTrack currentTrack;
    private boolean loopEnabled = false;
    public AtomicBoolean dequeueTimedOut = new AtomicBoolean(false);
    private static final File SAVEDTRACKS_FILE = new File(DiscordBot.configDirectory + "saved_tracks.json");
    public static ArrayList<SavedTrack> savedTracks = new ArrayList<>();
    private static final Gson gson = new Gson();

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;
    private static final double RETRY_DELAY_MULTIPLIER = 2.0;

    public MusicBot() {
        System.setProperty("http.keepAlive", "true");
        System.setProperty("http.maxConnections", "10");
        System.setProperty("sun.net.client.defaultConnectTimeout", "30000");
        System.setProperty("sun.net.client.defaultReadTimeout", "30000");

        playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerLocalSource(playerManager);

        YoutubeSourceOptions options = new YoutubeSourceOptions()
                .setRemoteCipher("https://cipher.kikkia.dev/", null, "xryzo11")
                .setAllowSearch(true)
                .setAllowDirectVideoIds(true)
                .setAllowDirectPlaylistIds(true);
        YoutubeAudioSourceManager yt = new YoutubeAudioSourceManager(options, new Client[]{new Music(), new Web(), new Ios(), new TvHtml5EmbeddedWithThumbnail(), new TvHtml5Embedded(), new Tv()});
        if (Config.getGoogleOAuth2Token() != null && !Config.getGoogleOAuth2Token().isEmpty() && !Config.getGoogleOAuth2Token().equals("YOUR_OAUTH2_TOKEN_HERE")) {
            yt.useOauth2(Config.getGoogleOAuth2Token(), true);
        } else {
            yt.useOauth2(null, false);
        }

        try {
            SpotifySourceManager spotifyManager = new SpotifySourceManager(
                    null,
                    Config.getSpotifyClientId(),
                    Config.getSpotifyClientSecret(),
                    "US",
                    playerManager
            );
            playerManager.registerSourceManager(spotifyManager);
            if (BotSettings.isDebug()) {
                System.out.println(DiscordBot.getTimestamp() + "[MusicBot] Spotify source manager initialized successfully");
            }
        } catch (Exception e) {
            System.err.println("[MusicBot] Failed to initialize Spotify: " + e.getMessage());
            if (BotSettings.isDebug()) {
                e.printStackTrace();
            }
        }

        playerManager.registerSourceManager(yt);
        AudioSourceManagers.registerRemoteSources(playerManager);
        player = playerManager.createPlayer();
        playerManager.getConfiguration().setFilterHotSwapEnabled(true);

        loadFromFile();

        player.addListener(new AudioEventAdapter() {
            @Override
            public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
                boolean isTimeout = exception.getCause() instanceof java.net.SocketTimeoutException;

                if (isTimeout) {
                    Integer retryCount = (Integer) track.getUserData(Integer.class);
                    if (retryCount == null) retryCount = 0;

                    if (retryCount < 2) {
                        track.setUserData(retryCount + 1);
                        player.playTrack(track.makeClone());
                        if (BotSettings.isDebug()) {
                            System.out.println(DiscordBot.getTimestamp() + "[player] Retrying track due to timeout (attempt " + (retryCount + 1) + ")");
                        }
                        return;
                    }
                }

                playNextTrack();
            }

            @Override
            public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
                currentTrack = null;
                if (endReason.mayStartNext) {
                    if (loopEnabled && track != null && endReason != AudioTrackEndReason.LOAD_FAILED) {
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

    public static void playNextTrack() {
        AudioTrack nextTrack = trackQueue.poll();
        if (containsAsmr(nextTrack)) {
            if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[queue] Blocked ASMR track: " + nextTrack.getInfo().title + " from queue.");
            playNextTrack();
            return;
        }
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
        String title = track.getInfo().title;
        String youtubeUrl = track.getInfo().uri;
        long durationMs = track.getDuration();

        String durationStr;
        if (durationMs < 0 || durationMs >= TimeUnit.HOURS.toMillis(50)) {
            durationStr = "Unknown";
        } else {
            long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs);
            long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60;
            durationStr = String.format("%d:%02d", minutes, seconds);
        }

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

    public void queue(SlashCommandInteractionEvent event, String url, boolean isPlayNext) {
        SlashCommands.safeDefer(event);
        queue(event.getHook(), url, isPlayNext);
    }

    public void queue(InteractionHook hook, String url, boolean isPlayNext) {
        hook.editOriginal("⏳ Loading...").queue();
        queueWithRetry(hook, url, 0, isPlayNext);
    }

    private void queueWithRetry(InteractionHook hook, String url, int attemptNumber, boolean isPlayNext) {
        playerManager.loadItem(url, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                if (containsAsmr(track)) {
                    hook.editOriginal("❌ ASMR content is blocked.").queue();
                    if (BotSettings.isDebug())
                        System.out.println(DiscordBot.getTimestamp() + "[queue] Blocked ASMR track: " + track.getInfo().title + " from URL: " + url);
                    return;
                }
                try {
                    track.setUserData(hook);
                    if (!isPlayNext) {
                        trackQueue.offerLast(track);
                    } else {
                        trackQueue.offerFirst(track);
                    }
                    String msg = player.getPlayingTrack() == null
                            ? "✅ Now playing: " + track.getInfo().title
                            : (isPlayNext
                            ? "✅ Queued next: " + track.getInfo().title
                            : "✅ Queued: " + track.getInfo().title);
                    hook.editOriginal(msg).queue();
                    if (player.getPlayingTrack() == null) {
                        playNextTrack();
                    }
                    if (BotSettings.isDebug()) {
                        System.out.println(
                                DiscordBot.getTimestamp() +
                                        (isPlayNext ? "[queue] Loaded track to play next: " : "[queue] Loaded track: ") +
                                        track.getInfo().title + " from URL: " + url
                        );
                    }
                } catch (Exception e) {
                    hook.editOriginal("❌ Error queuing track: " + e.getMessage()).queue();
                    if (BotSettings.isDebug())
                        System.out.println(DiscordBot.getTimestamp() + "[queue] Exception while queuing track from URL: " + url + " Error: " + e.getMessage());
                }
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.getTracks().isEmpty()) {
                    hook.editOriginal("❌ Cannot queue an empty playlist").queue();
                    if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[search] Cannot queue an empty playlist: " + url);
                    return;
                }
                if (Config.isLimitPlaylistEnabled()) {
                    if (playlist.getTracks().size() > 500) {
                        hook.editOriginal("❌ Playlist exceeds maximum allowed size of " + 500 + " tracks. (" + playlist.getTracks().size() + " tracks)").queue();
                        if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[search] Playlist size " + playlist.getTracks().size() + " exceeds limit for query: " + url);
                        return;
                    }
                }
                try {
                    playlist.getTracks().forEach(track -> {
                        track.setUserData(hook);
                        trackQueue.offer(track);
                    });
                    hook.editOriginal("✅ Added " + playlist.getTracks().size() + " tracks").queue();
                    if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[queue] Loaded playlist: " + playlist.getName() + " with " + playlist.getTracks().size() + " tracks from URL: " + url);
                    if (player.getPlayingTrack() == null) playNextTrack();
                } catch (Exception e) {
                    hook.editOriginal("❌ Error queuing playlist: " + e.getMessage()).queue();
                    if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[queue] Exception while queuing playlist from URL: " + url + " Error: " + e.getMessage());
                }
            }

            @Override
            public void noMatches() {
                hook.editOriginal("❌ Not found").queue();
                if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[queue] No matches found for URL: " + url);
            }

            @Override
            public void loadFailed(FriendlyException e) {
                boolean isTimeout = e.getCause() instanceof java.net.SocketTimeoutException;
                boolean isSpotify = url.contains("spotify.com");

                if (attemptNumber < MAX_RETRIES - 1 && isTimeout) {
                    long delay = (long) (INITIAL_RETRY_DELAY_MS * Math.pow(RETRY_DELAY_MULTIPLIER, attemptNumber));

                    if (BotSettings.isDebug()) {
                        System.out.println(DiscordBot.getTimestamp() + "[queue] Retry " + (attemptNumber + 1) + "/" + MAX_RETRIES +
                                         " for URL: " + url + " after " + delay + "ms due to timeout");
                    }

                    String retryMsg = isSpotify
                        ? "⏳ Spotify is slow to respond, retrying... (attempt " + (attemptNumber + 2) + "/" + MAX_RETRIES + ")"
                        : "⏳ Retrying... (attempt " + (attemptNumber + 2) + "/" + MAX_RETRIES + ")";
                    hook.editOriginal(retryMsg).queue();

                    CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute(() -> {
                        queueWithRetry(hook, url, attemptNumber + 1, isPlayNext);
                    });
                } else {
                    String errorMsg;
                    if (isTimeout && isSpotify) {
                        errorMsg = attemptNumber > 0
                            ? "❌ Spotify timed out after " + (attemptNumber + 1) + " attempts. Their API may be slow/down. Try again later or use a YouTube link instead."
                            : "❌ Spotify connection timed out. Try again or use a YouTube link instead.";
                    } else {
                        errorMsg = attemptNumber > 0
                            ? "❌ Failed after " + (attemptNumber + 1) + " attempts: " + e.getMessage()
                            : "❌ Failed: " + e.getMessage();
                    }
                    hook.editOriginal(errorMsg).queue();

                    if (BotSettings.isDebug()) {
                        System.out.println(DiscordBot.getTimestamp() + "[queue] Load failed for URL: " + url + " Error: " + e.getMessage());
                        if (e.getCause() != null) {
                            System.out.println(DiscordBot.getTimestamp() + "[queue] Cause: " + e.getCause().getClass().getSimpleName() + " - " + e.getCause().getMessage());
                        }
                    }
                }
            }
        });
    }


    public void search(SlashCommandInteractionEvent event, String query) {
        SlashCommands.safeDefer(event);
        InteractionHook hook = event.getHook();
        hook.editOriginal("⏳ Searching for: " + query).queue();
        if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[search] Searching for query: " + query);
        searchWithRetry(hook, query, 0);
    }

    private void searchWithRetry(InteractionHook hook, String query, int attemptNumber) {
        playerManager.loadItem("ytsearch:" + query, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                if (containsAsmr(track)) {
                    hook.editOriginal("❌ ASMR content is blocked.").queue();
                    if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[queue] Blocked ASMR track: " + track.getInfo().title + " from search.");
                    return;
                }
                try {
                    track.setUserData(hook);
                    trackQueue.offer(track);
                    String msg = player.getPlayingTrack() == null
                            ? "✅ Now playing: " + track.getInfo().title
                            : "✅ Queued: " + track.getInfo().title;
                    hook.editOriginal(msg).queue();
                    if (player.getPlayingTrack() == null) playNextTrack();
                    if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[search] Loaded track: " + track.getInfo().title + " for query: " + query);
                } catch (Exception e) {
                    hook.editOriginal("❌ Error queuing track: " + e.getMessage()).queue();
                    if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[search] Exception while queuing track for query: " + query + " Error: " + e.getMessage());
                }
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.getTracks().isEmpty()) {
                    hook.editOriginal("❌ No results found for: " + query).queue();
                    if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[search] No matches found for query: " + query);
                    return;
                }
                if (Config.isLimitPlaylistEnabled()) {
                    if (playlist.getTracks().size() > 500) {
                        hook.editOriginal("❌ Playlist exceeds maximum allowed size of " + 500 + " tracks. (" + playlist.getTracks().size() + " tracks)").queue();
                        if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[search] Playlist size " + playlist.getTracks().size() + " exceeds limit for query: " + query);
                        return;
                    }
                }
                try {
                    AudioTrack firstTrack = playlist.getTracks().get(0);
                    if (containsAsmr(firstTrack)) {
                        hook.editOriginal("❌ ASMR content is blocked.").queue();
                        if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[queue] Blocked ASMR track: " + firstTrack.getInfo().title + " from search playlist.");
                        return;
                    }
                    firstTrack.setUserData(hook);
                    trackQueue.offer(firstTrack);
                    String msg = player.getPlayingTrack() == null
                            ? "✅ Now playing: " + firstTrack.getInfo().title
                            : "✅ Queued: " + firstTrack.getInfo().title;
                    hook.editOriginal(msg).queue();
                    if (player.getPlayingTrack() == null) playNextTrack();
                    if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[search] Loaded first track from playlist: " + playlist.getName() + " for query: " + query);
                } catch (Exception e) {
                    hook.editOriginal("❌ Error queuing track: " + e.getMessage()).queue();
                    if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[search] Exception while queuing track from playlist for query: " + query + " Error: " + e.getMessage());
                }
            }

            @Override
            public void noMatches() {
                hook.editOriginal("❌ No results found for: " + query).queue();
                if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[search] No matches found for query: " + query);
            }

            @Override
            public void loadFailed(FriendlyException e) {
                boolean isTimeout = e.getCause() instanceof java.net.SocketTimeoutException;

                if (attemptNumber < MAX_RETRIES - 1 && isTimeout) {
                    long delay = (long) (INITIAL_RETRY_DELAY_MS * Math.pow(RETRY_DELAY_MULTIPLIER, attemptNumber));

                    if (BotSettings.isDebug()) {
                        System.out.println(DiscordBot.getTimestamp() + "[search] Retry " + (attemptNumber + 1) + "/" + MAX_RETRIES +
                                         " for query: " + query + " after " + delay + "ms due to timeout");
                    }

                    hook.editOriginal("⏳ Retrying search... (attempt " + (attemptNumber + 2) + "/" + MAX_RETRIES + ")").queue();

                    CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute(() -> {
                        searchWithRetry(hook, query, attemptNumber + 1);
                    });
                } else {
                    String errorMsg = attemptNumber > 0
                        ? "❌ Search failed after " + (attemptNumber + 1) + " attempts: " + e.getMessage()
                        : "❌ Search failed: " + e.getMessage();
                    hook.editOriginal(errorMsg).queue();

                    if (BotSettings.isDebug()) {
                        System.out.println(DiscordBot.getTimestamp() + "[search] Load failed for query: " + query + " Error: " + e.getMessage());
                    }
                }
            }
        });
    }

    public void save(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        String url = event.getOption("url").getAsString();
        String name = event.getOption("name").getAsString();

        event.getHook().editOriginal("⏳ Saving track...").queue();
        if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[save] Saving URL: " + url + " with name: " + name);

        for (SavedTrack track : savedTracks) {
            if (track.getName().equalsIgnoreCase(name)) {
                event.getHook().editOriginal("❌ A saved track with that name already exists: " + name).queue();
                if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[save] Duplicate saved track name: " + name);
                return;
            }
        }
        try {
            SavedTrack newTrack = new SavedTrack(name, url);
            savedTracks.add(newTrack);
            saveToFile();
            event.getHook().editOriginal("✅ Saved track: " + name).queue();
            if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[save] Successfully saved track: " + name + " with URL: " + url);
        } catch (Exception e) {
            event.getHook().editOriginal("❌ Failed to save track: " + e.getMessage()).queue();
            if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[save] Exception while saving track: " + e.getMessage());
            return;
        }
    }

    public void addSaved(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        event.getHook().editOriginal("⏳ Adding saved track...").queue();
        if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[addSaved] Adding saved track...");

        String name = event.getOption("track").getAsString();
        for (SavedTrack track : savedTracks) {
            if (track.getName().equalsIgnoreCase(name)) {
                if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[addSaved] Found saved track: " + name + " with URL: " + track.getUrl());
                queue(event.getHook(), track.getUrl(), false);
                return;
            }
        }
        event.getHook().editOriginal("❌ Saved track not found: " + name).queue();
        if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[addSaved] Saved track not found: " + name);
    }

    public void saveToFile() {
        try {
            File file = SAVEDTRACKS_FILE;
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (Writer writer = new FileWriter(file)) {
                gson.toJson(savedTracks, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadFromFile() {
        try (Reader reader = new FileReader(SAVEDTRACKS_FILE)) {
            Type listType = new TypeToken<List<SavedTrack>>(){}.getType();
            savedTracks = gson.fromJson(reader, listType);
            if (savedTracks == null) savedTracks = new ArrayList<>();
        } catch (IOException e) {
            savedTracks = new ArrayList<>();
        }
    }

    public void dequeueTrack(SlashCommandInteractionEvent event, int position) {
        event.deferReply().queue();
        event.getHook().editOriginal("⏳ Finding track in queue...").queue();
        if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[dequeueTrack] Attempting to dequeue track at position: " + position);

        AudioTrack toRemove = null;
        Iterator<AudioTrack> queueIterator = trackQueue.iterator();
        for (int i = 1; queueIterator.hasNext(); i++) {
            AudioTrack track = queueIterator.next();
            if (i == position) {
                toRemove = track;
                if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[dequeueTrack] Found track to remove: " + formatTrackInfo(toRemove));
                break;
            }
        }

        if (toRemove != null) {
            AudioTrack trackToRemove = toRemove;
            event.getHook().editOriginal("⚠️ Found track: " + formatTrackInfo(trackToRemove) + "\nReact with ✅ to confirm or ❌ to cancel.").queue(message -> {
                ConfirmationHandler confirmation = new ConfirmationHandler(message, event.getUser(), 60);
                ReactionListener.registerConfirmation(message.getId(), confirmation);

                confirmation.awaitConfirmation().thenAccept(result -> {
                    ReactionListener.unregisterConfirmation(message.getId());

                    switch (result) {
                        case CONFIRMED:
                            if (!trackQueue.contains(trackToRemove)) {
                                event.getHook().editOriginal("❗ Track was already removed from the queue.").queue();
                                if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[dequeueTrack] Track was already removed from the queue.");
                                return;
                            }
                            trackQueue.remove(trackToRemove);
                            event.getHook().editOriginal("✅ Removed track from queue: " + formatTrackInfo(trackToRemove)).queue();
                            if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[dequeueTrack] Successfully removed track: " + formatTrackInfo(trackToRemove));
                            break;
                        case CANCELLED:
                            event.getHook().editOriginal("❌ Dequeue cancelled.").queue();
                            if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[dequeueTrack] Dequeue cancelled by user.");
                            break;
                        case TIMEOUT:
                            event.getHook().editOriginal("❗ Dequeue timed out. Track was not removed.").queue();
                            if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[dequeueTrack] Dequeue timed out.");
                            break;
                    }
                });
            });
        } else {
            event.getHook().editOriginal("❌ Invalid position").queue();
            if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[dequeueTrack] Invalid position: " + position);
        }
    }


    public void reorderTrack(SlashCommandInteractionEvent event, int fromPosition, int toPosition) {
        event.deferReply().queue();
        event.getHook().editOriginal("⏳ Finding track in queue...").queue();
        if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[reorderTrack] Attempting to move track from position " + fromPosition + " to " + toPosition);

        if (fromPosition == toPosition) {
            event.getHook().editOriginal("❌ 'From' and 'To' positions are the same. No changes made.").queue();
            if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[reorderTrack] 'From' and 'To' positions are the same.");
            return;
        }

        List<AudioTrack> trackList = new ArrayList<>(trackQueue);
        if (fromPosition < 1 || fromPosition > trackList.size() || toPosition < 1 || toPosition > trackList.size()) {
            event.getHook().editOriginal("❌ Invalid 'From' or 'To' position.").queue();
            if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[reorderTrack] Invalid 'From' or 'To' position.");
            return;
        }

        AudioTrack trackToMove = trackList.get(fromPosition - 1);
        event.getHook().editOriginal("⚠️ Move this track from position " + fromPosition + " to position " + toPosition + "?\n" + formatTrackInfo(trackToMove) + "\nReact with ✅ to confirm or ❌ to cancel.").queue(message -> {
            ConfirmationHandler confirmation = new ConfirmationHandler(message, event.getUser(), 60);
            ReactionListener.registerConfirmation(message.getId(), confirmation);

            confirmation.awaitConfirmation().thenAccept(result -> {
                ReactionListener.unregisterConfirmation(message.getId());

                switch (result) {
                    case CONFIRMED:
                        List<AudioTrack> currentTrackList = new ArrayList<>(trackQueue);
                        if (fromPosition > currentTrackList.size() || toPosition > currentTrackList.size()) {
                            event.getHook().editOriginal("❗ Queue has changed. Please try again.").queue();
                            if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[reorderTrack] Queue changed during confirmation.");
                            return;
                        }

                        AudioTrack track = currentTrackList.remove(fromPosition - 1);
                        currentTrackList.add(toPosition - 1, track);

                        trackQueue.clear();
                        trackQueue.addAll(currentTrackList);

                        event.getHook().editOriginal("✅ Moved track to position " + toPosition + ": " + formatTrackInfo(track)).queue();
                        if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[reorderTrack] Successfully moved track to position " + toPosition + ": " + formatTrackInfo(track));
                        break;
                    case CANCELLED:
                        event.getHook().editOriginal("❌ Reorder cancelled.").queue();
                        if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[reorderTrack] Reorder cancelled by user.");
                        break;
                    case TIMEOUT:
                        event.getHook().editOriginal("❗ Reorder timed out. Track was not moved.").queue();
                        if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[reorderTrack] Reorder timed out.");
                        break;
                }
            });
        });
    }


    public static boolean containsAsmr(AudioTrack track) {
        if (track == null || !Config.isAsmrBlockEnabled()) {
            return false;
        }
        String titleLower = track.getInfo().title.toLowerCase();
        if (titleLower.contains("asmr") || titleLower.contains("f4f") || titleLower.contains("f4m") || titleLower.contains("f4a") || titleLower.contains("m4f") || titleLower.contains("m4m") || titleLower.contains("m4a") || titleLower.contains("a4f") || titleLower.contains("a4m") || titleLower.contains("a4a")) {
            return true;
        }
        return false;
    }
}
