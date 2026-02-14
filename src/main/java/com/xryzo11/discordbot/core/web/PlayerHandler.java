package com.xryzo11.discordbot.core.web;

import com.google.gson.Gson;
import com.xryzo11.discordbot.DiscordBot;
import spark.Request;
import spark.Response;

import java.util.HashMap;
import java.util.Map;

import static spark.Spark.*;

public class PlayerHandler {
    private static final Gson gson = new Gson();

    public static void registerRoutes() {
        get("/player/obs.html", PlayerHandler::getObsHtml);
        get("/player/browser.html", PlayerHandler::getBrowserHtml);

        get("/current-track", PlayerHandler::getCurrentTrack, gson::toJson);
        get("/queue", PlayerHandler::getQueue, gson::toJson);
        get("/bot-status", PlayerHandler::getBotStatus, gson::toJson);

        post("/music/play-pause", PlayerHandler::playPause, gson::toJson);
        post("/music/skip", PlayerHandler::skip, gson::toJson);
        post("/music/stop", PlayerHandler::stop, gson::toJson);
        post("/music/shuffle", PlayerHandler::shuffle, gson::toJson);
        post("/music/loop", PlayerHandler::loop, gson::toJson);
        post("/music/clear", PlayerHandler::clearQueue, gson::toJson);
        post("/music/remove", PlayerHandler::removeTrack, gson::toJson);
        post("/music/add", PlayerHandler::addTrack, gson::toJson);
        post("/music/seek", PlayerHandler::seek, gson::toJson);
        post("/music/reorder", PlayerHandler::reorder, gson::toJson);
        post("/music/join-channel", PlayerHandler::joinChannel, gson::toJson);
    }

    private static Object getObsHtml(Request req, Response res) {
        res.type("text/html");
        try (var stream = PlayerHandler.class.getResourceAsStream("/public/player/obs.html")) {
            if (stream == null) {
                res.status(404);
                return "File not found";
            }
            return new String(stream.readAllBytes());
        } catch (Exception e) {
            res.status(500);
            return "Error loading file: " + e.getMessage();
        }
    }

    private static Object getBrowserHtml(Request req, Response res) {
        res.type("text/html");
        try (var stream = PlayerHandler.class.getResourceAsStream("/public/player/browser.html")) {
            if (stream == null) {
                res.status(404);
                return "File not found";
            }
            return new String(stream.readAllBytes());
        } catch (Exception e) {
            res.status(500);
            return "Error loading file: " + e.getMessage();
        }
    }

    private static Object getCurrentTrack(Request req, Response res) {
        res.type("application/json");
        Map<String, Object> data = new HashMap<>();

        if (DiscordBot.musicBot != null && DiscordBot.musicBot.currentTrack != null) {
            var track = DiscordBot.musicBot.currentTrack;
            data.put("playing", true);
            data.put("title", track.getInfo().title);
            data.put("author", track.getInfo().author);
            data.put("uri", track.getInfo().uri);
            data.put("position", track.getPosition());
            data.put("duration", track.getDuration());
            data.put("isStream", track.getInfo().isStream);
            data.put("artworkUrl", track.getInfo().artworkUrl);
            data.put("userId", DiscordBot.musicBot.currentTrackUserId);

            if (DiscordBot.musicBot.currentTrackUserId != null) {
                var jda = com.xryzo11.discordbot.core.BotHolder.getJDA();
                if (jda != null) {
                    try {
                        var user = jda.retrieveUserById(DiscordBot.musicBot.currentTrackUserId).complete();
                        if (user != null) {
                            data.put("userName", user.getEffectiveName());
                            data.put("userAvatarUrl", user.getEffectiveAvatarUrl());
                        }
                    } catch (Exception e) {
                        // User not found or error, skip
                    }
                }
            }
        } else {
            data.put("playing", false);
        }

        return data;
    }

    private static Object getQueue(Request req, Response res) {
        res.type("application/json");
        Map<String, Object> data = new HashMap<>();

        if (DiscordBot.musicBot != null) {
            var queue = new java.util.ArrayList<Map<String, Object>>();
            int index = 0;
            var jda = com.xryzo11.discordbot.core.BotHolder.getJDA();

            for (var queuedTrack : DiscordBot.musicBot.trackQueue) {
                var track = queuedTrack.getTrack();
                Map<String, Object> trackData = new HashMap<>();
                trackData.put("index", index++);
                trackData.put("title", track.getInfo().title);
                trackData.put("author", track.getInfo().author);
                trackData.put("uri", track.getInfo().uri);
                trackData.put("duration", track.getDuration());
                trackData.put("artworkUrl", track.getInfo().artworkUrl);
                trackData.put("userId", queuedTrack.getUserId());

                if (queuedTrack.getUserId() != null && jda != null) {
                    try {
                        var user = jda.retrieveUserById(queuedTrack.getUserId()).complete();
                        if (user != null) {
                            trackData.put("userName", user.getEffectiveName());
                            trackData.put("userAvatarUrl", user.getEffectiveAvatarUrl());
                        }
                    } catch (Exception e) {
                        // User not found or error, skip
                    }
                }

                queue.add(trackData);
            }
            data.put("queue", queue);
            data.put("queueSize", queue.size());
            data.put("loopEnabled", DiscordBot.musicBot.isLoopEnabled());
            data.put("isPaused", DiscordBot.musicBot.player.isPaused());
        } else {
            data.put("queue", new java.util.ArrayList<>());
            data.put("queueSize", 0);
        }

        return data;
    }

    private static Object getBotStatus(Request req, Response res) {
        res.type("application/json");
        Map<String, Object> data = new HashMap<>();

        var jda = com.xryzo11.discordbot.core.BotHolder.getJDA();
        boolean isConnected = false;
        String channelName = null;
        boolean userInVoiceChannel = false;
        String userChannelId = null;

        if (jda != null) {
            for (var guild : jda.getGuilds()) {
                if (guild.getAudioManager().isConnected()) {
                    isConnected = true;
                    var channel = guild.getAudioManager().getConnectedChannel();
                    if (channel != null) {
                        channelName = channel.getName();
                    }
                    break;
                }
            }

            String sessionToken = req.queryParams("sessionToken");
            if (sessionToken != null) {
                var session = com.xryzo11.discordbot.core.web.SessionManager.getSession(sessionToken);
                if (session != null && !session.getUserId().equals("password-user")) {
                    String userId = session.getUserId();
                    for (var guild : jda.getGuilds()) {
                        var member = guild.getMemberById(userId);
                        if (member != null && member.getVoiceState() != null && member.getVoiceState().inAudioChannel()) {
                            userInVoiceChannel = true;
                            var userChannel = member.getVoiceState().getChannel();
                            if (userChannel != null) {
                                userChannelId = userChannel.getId();
                            }
                            break;
                        }
                    }
                }
            }
        }

        data.put("inVoiceChannel", isConnected);
        data.put("channelName", channelName);
        data.put("userInVoiceChannel", userInVoiceChannel);
        data.put("userChannelId", userChannelId);

        return data;
    }

    private static Object playPause(Request req, Response res) {
        res.type("application/json");
        Map<String, Object> result = new HashMap<>();

        if (DiscordBot.musicBot == null) {
            result.put("success", false);
            result.put("message", "Music bot not initialized");
            System.out.println(DiscordBot.getTimestamp() + "[web-player] Play/pause failed: Music bot not initialized");
            return result;
        }

        if (DiscordBot.musicBot.player.isPaused()) {
            DiscordBot.musicBot.resumePlayer();
            result.put("success", true);
            result.put("paused", false);
            System.out.println(DiscordBot.getTimestamp() + "[web-player] Player resumed");
        } else {
            DiscordBot.musicBot.pausePlayer();
            result.put("success", true);
            result.put("paused", true);
            System.out.println(DiscordBot.getTimestamp() + "[web-player] Player paused");
        }

        return result;
    }

    private static Object skip(Request req, Response res) {
        res.type("application/json");
        Map<String, Object> result = new HashMap<>();

        if (DiscordBot.musicBot == null) {
            result.put("success", false);
            result.put("message", "Music bot not initialized");
            System.out.println(DiscordBot.getTimestamp() + "[web-player] Skip failed: Music bot not initialized");
            return result;
        }

        String trackTitle = DiscordBot.musicBot.currentTrack != null ? DiscordBot.musicBot.currentTrack.getInfo().title : "Unknown";
        DiscordBot.musicBot.skipCurrentTrack();
        result.put("success", true);
        System.out.println(DiscordBot.getTimestamp() + "[web-player] Skipped track: " + trackTitle);

        return result;
    }

    private static Object stop(Request req, Response res) {
        res.type("application/json");
        Map<String, Object> result = new HashMap<>();

        if (DiscordBot.musicBot == null) {
            result.put("success", false);
            result.put("message", "Music bot not initialized");
            System.out.println(DiscordBot.getTimestamp() + "[web-player] Stop failed: Music bot not initialized");
            return result;
        }

        DiscordBot.musicBot.stopPlayer();
        result.put("success", true);
        System.out.println(DiscordBot.getTimestamp() + "[web-player] Player stopped");

        return result;
    }

    private static Object shuffle(Request req, Response res) {
        res.type("application/json");
        Map<String, Object> result = new HashMap<>();

        if (DiscordBot.musicBot == null) {
            result.put("success", false);
            result.put("message", "Music bot not initialized");
            System.out.println(DiscordBot.getTimestamp() + "[web-player] Shuffle failed: Music bot not initialized");
            return result;
        }

        int queueSize = DiscordBot.musicBot.trackQueue.size();
        DiscordBot.musicBot.shuffleQueue();
        result.put("success", true);
        System.out.println(DiscordBot.getTimestamp() + "[web-player] Queue shuffled (" + queueSize + " tracks)");

        return result;
    }

    private static Object loop(Request req, Response res) {
        res.type("application/json");
        Map<String, Object> result = new HashMap<>();

        if (DiscordBot.musicBot == null) {
            result.put("success", false);
            result.put("message", "Music bot not initialized");
            System.out.println(DiscordBot.getTimestamp() + "[web-player] Loop toggle failed: Music bot not initialized");
            return result;
        }

        DiscordBot.musicBot.toggleLoop();
        result.put("success", true);
        result.put("loopEnabled", DiscordBot.musicBot.isLoopEnabled());
        System.out.println(DiscordBot.getTimestamp() + "[web-player] Loop " + (DiscordBot.musicBot.isLoopEnabled() ? "enabled" : "disabled"));

        return result;
    }

    private static Object clearQueue(Request req, Response res) {
        res.type("application/json");
        Map<String, Object> result = new HashMap<>();

        if (DiscordBot.musicBot == null) {
            result.put("success", false);
            result.put("message", "Music bot not initialized");
            System.out.println(DiscordBot.getTimestamp() + "[web-player] Clear queue failed: Music bot not initialized");
            return result;
        }

        int queueSize = DiscordBot.musicBot.trackQueue.size();
        DiscordBot.musicBot.clearQueue();
        result.put("success", true);
        System.out.println(DiscordBot.getTimestamp() + "[web-player] Queue cleared (" + queueSize + " tracks removed)");

        return result;
    }

    private static Object removeTrack(Request req, Response res) {
        res.type("application/json");
        Map<String, Object> result = new HashMap<>();

        if (DiscordBot.musicBot == null) {
            result.put("success", false);
            result.put("message", "Music bot not initialized");
            System.out.println(DiscordBot.getTimestamp() + "[web-player] Remove track failed: Music bot not initialized");
            return result;
        }

        try {
            int position = Integer.parseInt(req.queryParams("position"));
            var queue = new java.util.ArrayList<>(DiscordBot.musicBot.trackQueue);

            if (position < 0 || position >= queue.size()) {
                result.put("success", false);
                result.put("message", "Invalid position");
                System.out.println(DiscordBot.getTimestamp() + "[web-player] Remove track failed: Invalid position " + position);
                return result;
            }

            var queuedTrack = queue.get(position);
            DiscordBot.musicBot.trackQueue.remove(queuedTrack);
            result.put("success", true);
            result.put("removed", queuedTrack.getTrack().getInfo().title);
            System.out.println(DiscordBot.getTimestamp() + "[web-player] Removed track at position " + position + ": " + queuedTrack.getTrack().getInfo().title);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            System.out.println(DiscordBot.getTimestamp() + "[web-player] Remove track failed: " + e.getMessage());
        }

        return result;
    }

    private static Object addTrack(Request req, Response res) {
        res.type("application/json");
        Map<String, Object> result = new HashMap<>();

        if (DiscordBot.musicBot == null) {
            result.put("success", false);
            result.put("message", "Music bot not initialized");
            System.out.println(DiscordBot.getTimestamp() + "[web-player] Add track failed: Music bot not initialized");
            return result;
        }

        try {
            String url = req.queryParams("url");
            if (url == null || url.isEmpty()) {
                result.put("success", false);
                result.put("message", "URL is required");
                System.out.println(DiscordBot.getTimestamp() + "[web-player] Add track failed: URL is required");
                return result;
            }

            if (!url.contains("youtube.com") && !url.contains("youtu.be") && !url.contains("spotify.com")) {
                if (url.contains("http://") || url.contains("https://") || url.contains("www.")) {
                    result.put("success", false);
                    result.put("message", "Only YouTube and Spotify links are supported");
                    System.out.println(DiscordBot.getTimestamp() + "[web-player] Add track failed: Unsupported URL: " + url);
                    return result;
                }
                url = "ytsearch:" + url;
            }

            String sessionToken = req.queryParams("sessionToken");
            String userId = null;
            if (sessionToken != null) {
                var session = com.xryzo11.discordbot.core.web.SessionManager.getSession(sessionToken);
                if (session != null) {
                    userId = session.getUserId();
                }
            }

            result.put("success", true);
            result.put("message", "Loading track...");
            result.put("url", url);
            System.out.println(DiscordBot.getTimestamp() + "[web-player] Loading track: " + url);

            final String finalUrl = url;
            final String finalUserId = userId;
            new Thread(() -> {
                loadItemWithRetry(finalUrl, 0, finalUserId);
            }).start();

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            System.out.println(DiscordBot.getTimestamp() + "[web-player] Add track exception: " + e.getMessage());
        }

        return result;
    }

    private static void loadItemWithRetry(String url, int retryCount, String userId) {
        final int maxRetries = 2;

        DiscordBot.musicBot.playerManager.loadItem(url, new com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler() {
            @Override
            public void trackLoaded(com.sedmelluq.discord.lavaplayer.track.AudioTrack track) {
                if (userId != null) {
                    track.setUserData(userId);
                }
                DiscordBot.musicBot.trackQueue.offerLast(new com.xryzo11.discordbot.musicBot.QueuedTrack(track, userId));
                if (DiscordBot.musicBot.player.getPlayingTrack() == null) {
                    DiscordBot.musicBot.playNextTrack();
                }
                System.out.println(DiscordBot.getTimestamp() + "[web-player] Added track: " + track.getInfo().title);
            }

            @Override
            public void playlistLoaded(com.sedmelluq.discord.lavaplayer.track.AudioPlaylist playlist) {
                if (url.startsWith("ytsearch:")) {
                    if (playlist.getTracks().isEmpty()) {
                        System.out.println(DiscordBot.getTimestamp() + "[web-player] No matches found for search: " + url);
                        return;
                    }
                    com.sedmelluq.discord.lavaplayer.track.AudioTrack firstTrack = playlist.getTracks().get(0);
                    if (userId != null) {
                        firstTrack.setUserData(userId);
                    }
                    DiscordBot.musicBot.trackQueue.offerLast(new com.xryzo11.discordbot.musicBot.QueuedTrack(firstTrack, userId));
                    if (DiscordBot.musicBot.player.getPlayingTrack() == null) {
                        DiscordBot.musicBot.playNextTrack();
                    }
                    System.out.println(DiscordBot.getTimestamp() + "[web-player] Added first search result: " + firstTrack.getInfo().title);
                } else {
                    for (var track : playlist.getTracks()) {
                        if (userId != null) {
                            track.setUserData(userId);
                        }
                        DiscordBot.musicBot.trackQueue.offerLast(new com.xryzo11.discordbot.musicBot.QueuedTrack(track, userId));
                    }
                    if (DiscordBot.musicBot.player.getPlayingTrack() == null) {
                        DiscordBot.musicBot.playNextTrack();
                    }
                    System.out.println(DiscordBot.getTimestamp() + "[web-player] Added playlist: " + playlist.getName() + " (" + playlist.getTracks().size() + " tracks)");
                }
            }

            @Override
            public void noMatches() {
                System.out.println(DiscordBot.getTimestamp() + "[web-player] No matches found for: " + url);
            }

            @Override
            public void loadFailed(com.sedmelluq.discord.lavaplayer.tools.FriendlyException exception) {
                boolean isNetworkError = exception.getMessage().contains("timed out") ||
                                        exception.getMessage().contains("All clients failed") ||
                                        exception.getCause() instanceof java.net.SocketTimeoutException;

                if (isNetworkError && retryCount < maxRetries) {
                    int delayMs = (int) (1000 * Math.pow(2, retryCount));
                    System.out.println(DiscordBot.getTimestamp() + "[web-player] Load failed, retrying in " + delayMs + "ms (attempt " + (retryCount + 1) + "/" + maxRetries + "): " + exception.getMessage());

                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    loadItemWithRetry(url, retryCount + 1, userId);
                } else {
                    if (retryCount > 0) {
                        System.err.println(DiscordBot.getTimestamp() + "[web-player] Failed to load track after " + retryCount + " retries: " + exception.getMessage());
                    } else {
                        System.err.println(DiscordBot.getTimestamp() + "[web-player] Failed to load track: " + exception.getMessage());
                    }
                }
            }
        });
    }

    private static Object seek(Request req, Response res) {
        res.type("application/json");
        Map<String, Object> result = new HashMap<>();

        if (DiscordBot.musicBot == null) {
            result.put("success", false);
            result.put("message", "Music bot not initialized");
            System.out.println(DiscordBot.getTimestamp() + "[web-player] Seek failed: Music bot not initialized");
            return result;
        }

        try {
            long position = Long.parseLong(req.queryParams("position"));

            if (DiscordBot.musicBot.currentTrack == null) {
                result.put("success", false);
                result.put("message", "No track is currently playing");
                System.out.println(DiscordBot.getTimestamp() + "[web-player] Seek failed: No track playing");
                return result;
            }

            long duration = DiscordBot.musicBot.currentTrack.getDuration();
            if (position < 0 || position > duration) {
                result.put("success", false);
                result.put("message", "Invalid position");
                System.out.println(DiscordBot.getTimestamp() + "[web-player] Seek failed: Invalid position (" + position + "ms)");
                return result;
            }

            DiscordBot.musicBot.movePlayhead((int) position);
            result.put("success", true);
            result.put("position", position);
            System.out.println(DiscordBot.getTimestamp() + "[web-player] Seeked to position: " + DiscordBot.musicBot.formatTime(position / 1000) + " in track \"" + DiscordBot.musicBot.currentTrack.getInfo().title + "\"");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            System.out.println(DiscordBot.getTimestamp() + "[web-player] Seek exception: " + e.getMessage());
        }

        return result;
    }

    private static Object reorder(Request req, Response res) {
        res.type("application/json");
        Map<String, Object> result = new HashMap<>();

        if (DiscordBot.musicBot == null) {
            result.put("success", false);
            result.put("message", "Music bot not initialized");
            System.out.println(DiscordBot.getTimestamp() + "[web-player] Reorder track failed: Music bot not initialized");
            return result;
        }

        try {
            int from = Integer.parseInt(req.queryParams("from"));
            int to = Integer.parseInt(req.queryParams("to"));

            var queue = new java.util.ArrayList<>(DiscordBot.musicBot.trackQueue);

            if (from < 0 || from >= queue.size() || to < 0 || to >= queue.size()) {
                result.put("success", false);
                result.put("message", "Invalid positions");
                System.out.println(DiscordBot.getTimestamp() + "[web-player] Reorder track failed: Invalid positions (from: " + from + ", to: " + to + ")");
                return result;
            }

            var queuedTrack = queue.remove(from);
            queue.add(to, queuedTrack);

            DiscordBot.musicBot.trackQueue.clear();
            DiscordBot.musicBot.trackQueue.addAll(queue);

            result.put("success", true);
            System.out.println(DiscordBot.getTimestamp() + "[web-player] Reordered track: \"" + queuedTrack.getTrack().getInfo().title + "\" from position " + from + " to " + to);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            System.out.println(DiscordBot.getTimestamp() + "[web-player] Reorder track exception: " + e.getMessage());
        }

        return result;
    }

    private static Object joinChannel(Request req, Response res) {
        res.type("application/json");
        Map<String, Object> result = new HashMap<>();

        String sessionToken = req.queryParams("sessionToken");
        if (sessionToken == null) {
            result.put("success", false);
            result.put("message", "Not authenticated");
            return result;
        }

        var session = com.xryzo11.discordbot.core.web.SessionManager.getSession(sessionToken);
        if (session == null || session.getUserId().equals("password-user")) {
            result.put("success", false);
            result.put("message", "Invalid session");
            return result;
        }

        String userId = session.getUserId();
        var jda = com.xryzo11.discordbot.core.BotHolder.getJDA();

        if (jda == null) {
            result.put("success", false);
            result.put("message", "Bot not initialized");
            return result;
        }

        for (var guild : jda.getGuilds()) {
            var member = guild.getMemberById(userId);
            if (member != null && member.getVoiceState() != null && member.getVoiceState().inAudioChannel()) {
                var voiceChannel = member.getVoiceState().getChannel();
                if (voiceChannel != null) {
                    try {
                        guild.getAudioManager().openAudioConnection(voiceChannel);
                        result.put("success", true);
                        result.put("channelName", voiceChannel.getName());
                        System.out.println(DiscordBot.getTimestamp() + "[web-player] Bot joined voice channel: " + voiceChannel.getName());
                        return result;
                    } catch (Exception e) {
                        result.put("success", false);
                        result.put("message", "Failed to join channel: " + e.getMessage());
                        System.err.println(DiscordBot.getTimestamp() + "[web-player] Failed to join channel: " + e.getMessage());
                        return result;
                    }
                }
            }
        }

        result.put("success", false);
        result.put("message", "You are not in a voice channel");
        return result;
    }
}

