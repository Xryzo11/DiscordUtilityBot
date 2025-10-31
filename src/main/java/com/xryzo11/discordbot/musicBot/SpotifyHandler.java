package com.xryzo11.discordbot.musicBot;

import com.xryzo11.discordbot.DiscordBot;
import com.xryzo11.discordbot.core.BotSettings;
import com.xryzo11.discordbot.core.Config;
import com.xryzo11.discordbot.core.SlashCommands;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SpotifyHandler {
    private static final MusicBot bot = DiscordBot.musicBot;

    private static final int SPOTIFY_CONCURRENCY =
            Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors()));
    private static final ExecutorService SPOTIFY_EXEC =
            Executors.newFixedThreadPool(SPOTIFY_CONCURRENCY);

    public static void handleSpotifyUrl(SlashCommandInteractionEvent event, String url) {
        SlashCommands.safeDefer(event);
        event.getHook().editOriginal("⏳ Processing Spotify URL...").queue();
        if (BotSettings.isDebug())  System.out.println("[SpotifyHandler] Received Spotify URL: " + url);
        if (url.contains("spotify.com/track/")) {
            handleSpotifyTrack(event, url);
        } else if (url.contains("spotify.com/album/")) {
            event.getHook().editOriginal("❗ Album support is not yet implemented.").queue();
        } else if (url.contains("spotify.com/playlist/")) {
            handleSpotifyPlaylist(event, url);
        } else {
            event.getHook().editOriginal("❗ Unsupported Spotify URL. Please provide a track, album, or playlist URL.").queue();
        }
    }

    private static void handleSpotifyTrack(SlashCommandInteractionEvent event, String url) {
        event.getHook().editOriginal("⏳ Processing Spotify track...").queue();
        if (BotSettings.isDebug()) System.out.println("[SpotifyHandler] Handling Spotify track URL: " + url);

        int maxRetries = 5;
        int retryCount = 0;
        while (retryCount < maxRetries) {
            try {
                String ytUrl = resolveYoutubeUrlFromSpotify(url);
                if (ytUrl == null || ytUrl.isEmpty()) {
                    event.getHook().editOriginal("❌ Failed to retrieve YouTube URL from Spotify track.").queue();
                    if (BotSettings.isDebug()) System.out.println("[SpotifyHandler] No YouTube URL found in spotdl output.");
                    return;
                }
                bot.queue(event.getHook(), ytUrl, false);
                return;
            } catch (Exception e) {
                retryCount++;
                if (BotSettings.isDebug()) {
                    System.out.println("[SpotifyHandler] Attempt " + retryCount + " failed: " + e.getMessage());
                    e.printStackTrace();
                }
                if (retryCount >= maxRetries) {
                    event.getHook().editOriginal("❌ Spotdl failed after " + maxRetries + " retries.").queue();
                }
            }
        }
    }

    private static void handleSpotifyPlaylist(SlashCommandInteractionEvent event, String url) {
        InteractionHook hook = event.getHook();
        hook.editOriginal("⏳ Processing Spotify playlist...").queue();
        try {
            int totalTracks = getPlaylistLength(url.split("/playlist/")[1].split("\\?")[0]);
            if (Config.isLimitPlaylistEnabled()) {
                if (totalTracks > 250) {
                    hook.editOriginal("❗ Playlist has " + totalTracks + " tracks, which exceeds the limit of 250.").queue();
                    return;
                }
            }

            List<String> trackUrls = getPlaylistTrackUrls(event, url);
            if (trackUrls.isEmpty()) {
                hook.editOriginal("❌ No tracks found in playlist.").queue();
                return;
            }

            int total = trackUrls.size();
            AtomicInteger converted = new AtomicInteger(0);
            AtomicInteger failed = new AtomicInteger(0);

            hook.editOriginal("✅ Found " + total + " tracks. Converting and queuing...").queue();

            List<CompletableFuture<Void>> futures = new ArrayList<>(total);
            for (String trackUrl : trackUrls) {
                CompletableFuture<Void> f = CompletableFuture.supplyAsync(() -> {
                    if (MusicBot.isCancelled) {
                        return null;
                    }
                    try {
                        return resolveYoutubeUrlFromSpotify(trackUrl);
                    } catch (Exception e) {
                        if (BotSettings.isDebug()) {
                            System.out.println("[SpotifyHandler] Conversion failed: " + e.getMessage());
                        }
                        return null;
                    }
                }, SPOTIFY_EXEC).thenAccept(ytUrl -> {
                    if (ytUrl != null && !ytUrl.isEmpty()) {
                        converted.incrementAndGet();
                        bot.queueSilent(hook, ytUrl);
                    } else {
                        failed.incrementAndGet();
                    }

                    int done = converted.get() + failed.get();
                    if (done == 1 || done % 2 == 0 || done == total) {
                        if (MusicBot.isCancelled) {
                            hook.editOriginal("❌ Playlist processing cancelled.").queue();
                            return;
                        }
                        String summary = "🎵 Converted: " + converted.get() + "/" + total +
                                " | ❌ Failed: " + failed.get();
                        hook.editOriginal(summary).queue();
                    }

                });
                futures.add(f);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .whenComplete((v, ex) -> {
                        String finalMsg = "✅ Finished processing playlist. Converted: " + converted.get() +
                                "/" + total + " | ❌ Failed: " + failed.get();
                        hook.editOriginal(finalMsg).queue();
                    });

        } catch (Exception e) {
            e.printStackTrace();
            hook.editOriginal("❌ Failed to process playlist: " + e.getMessage()).queue();
        }
    }

    private static String resolveYoutubeUrlFromSpotify(String url) throws Exception {
        if (MusicBot.isCancelled) {
            throw new CancellationException();
        }

        List<String> command = new ArrayList<>();
        command.add("python3");
        command.add("-m");
        command.add("spotdl");
        command.add("url");
        command.add(url);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        String ytUrl = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (BotSettings.isDebug()) {
                    System.out.println("[spotdl] " + line);
                }
                if (line.contains("youtube.com/watch?v=") || line.contains("music.youtube.com/watch?v=")) {
                    ytUrl = line.trim();
                }
            }
        }
        process.waitFor();
        return ytUrl;
    }

    private static List<String> getPlaylistTrackUrls(SlashCommandInteractionEvent event, String playlistUrl) throws Exception {
        String playlistId = playlistUrl.split("/playlist/")[1].split("\\?")[0];

        event.getHook().editOriginal("⏳ Fetching playlist tracks...").queue();

        SpotifyApi spotifyApi = SpotifyClient.getApi();

        List<String> urls = new ArrayList<>();
        int offset = 0;
        int limit = 100;

        while (true) {
            final int currentOffset = offset;
            Paging<PlaylistTrack> tracks = SpotifyClient.withAuthRetry(() ->
                    spotifyApi
                            .getPlaylistsItems(playlistId)
                            .limit(limit)
                            .offset(currentOffset)
                            .build()
                            .execute()
            );

            if (tracks.getItems().length == 0) break;

            for (PlaylistTrack pt : tracks.getItems()) {
                if (pt == null || pt.getTrack() == null) continue;
                try {
                    String spotifyUrl = pt.getTrack().getExternalUrls() != null
                            ? pt.getTrack().getExternalUrls().get("spotify")
                            : null;
                    if (spotifyUrl != null && !spotifyUrl.isEmpty()) {
                        urls.add(spotifyUrl);
                    }
                } catch (Exception ignored) {}
            }

            offset += limit;
            if (tracks.getNext() == null) break;
        }

        return urls;
    }

    private static int getPlaylistLength(String playlistId) throws Exception {
        return SpotifyClient.withAuthRetry(() -> {
            Paging<PlaylistTrack> tracks = SpotifyClient.getApi()
                    .getPlaylistsItems(playlistId)
                    .limit(1)
                    .offset(0)
                    .build()
                    .execute();
            return tracks.getTotal();
        });
    }

}
