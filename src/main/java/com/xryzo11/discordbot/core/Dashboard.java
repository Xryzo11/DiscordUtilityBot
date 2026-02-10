package com.xryzo11.discordbot.core;

import static spark.Spark.*;
import com.google.gson.Gson;
import com.xryzo11.discordbot.DiscordBot;
import com.xryzo11.discordbot.misc.TempRoleManager;
import com.xryzo11.discordbot.misc.WywozBindingManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class Dashboard {
    public static void start() {
        Gson gson = new Gson();

        port(Config.getWebPort());
        staticFiles.location("/public");

        get("/dashboard/access.html", (req, res) -> {
            res.type("text/html");
            try (var stream = Dashboard.class.getResourceAsStream("/public/dashboard/access.html")) {
                if (stream == null) {
                    res.status(404);
                    return "File not found";
                }
                return new String(stream.readAllBytes());
            }
        });

        get("/player/obs.html", (req, res) -> {
            res.type("text/html");
            try (var stream = Dashboard.class.getResourceAsStream("/public/player/obs.html")) {
                if (stream == null) {
                    res.status(404);
                    return "File not found";
                }
                return new String(stream.readAllBytes());
            }
        });

        get("/player/browser.html", (req, res) -> {
            res.type("text/html");
            try (var stream = Dashboard.class.getResourceAsStream("/public/player/browser.html")) {
                if (stream == null) {
                    res.status(404);
                    return "File not found";
                }
                return new String(stream.readAllBytes());
            }
        });

        get("/web-auth", (req, res) -> {
            res.type("application/json");

            Map<String, Object> data = new HashMap<>();
            data.put("enabled", Config.isWebAuthEnabled());
            data.put("password", Config.getWebAuthPassword());

            return data;
        }, gson::toJson);

        get("/get-info", (req, res) -> {
            res.type("application/json");

            Map<String, Object> data = new HashMap<>();
            data.put("fullVersion", DiscordBot.fullVersion);
            data.put("lastRestart", DiscordBot.lastRestart);

            return data;
        }, gson::toJson);

        get("/wywoz-initial-status", (req, res) -> BotSettings.isWywozSmieciInitial() ? "enabled" : "disabled");
        get("/wywoz-status", (req, res) -> BotSettings.isWywozSmieciAuto() ? "enabled" : "disabled");
        post("/toggle-wywoz", (req, res) -> {
            boolean state = Boolean.parseBoolean(req.queryParams("status"));
            BotSettings.setWywozSmieci(state);
            res.redirect("/");
            System.out.println("[dashboard] Wywoz smieci toggled to: " + (state ? "ON" : "OFF"));
            return "Wywoz smieci toggled to: " + (state ? "ON" : "OFF");
        });

        get("/temp-role-initial-status", (req, res) -> BotSettings.isTempRoleInitial() ? "enabled" : "disabled");
        get("/temp-role-status", (req, res) -> BotSettings.isTempRoleAuto() ? "enabled" : "disabled");
        post("/toggle-temp-role-status", (req, res) -> {
            boolean state = Boolean.parseBoolean(req.queryParams("status"));
            BotSettings.setTempRole(state);
            res.redirect("/");
            System.out.println("[dashboard] Temporary role toggled to: " + (state ? "ON" : "OFF"));
            return "Temporary role toggled to: " + (state ? "ON" : "OFF");
        });

        post("/add-temp-role", (req, res) -> {
            String roleIdStr = req.queryParams("roleId");
            String channelIdStr = req.queryParams("channelId");
            long roleId = Long.parseUnsignedLong(roleIdStr);
            long channelId = Long.parseUnsignedLong(channelIdStr);
            TempRoleManager.addTempRole(roleId, channelId);
            return "OK";
        });

        post("/remove-temp-role", (req, res) -> {
            long roleId = Long.parseUnsignedLong(req.queryParams("roleId"));
            long channelId = Long.parseUnsignedLong(req.queryParams("channelId"));
            TempRoleManager.removeTempRole(roleId, channelId);
            return "OK";
        });

        post("/toggle-temp-role", (req, res) -> {
            String roleIdStr = req.queryParams("roleId");
            String channelIdStr = req.queryParams("channelId");
            boolean enabled = Boolean.parseBoolean(req.queryParams("enabled"));
            long roleId = Long.parseUnsignedLong(roleIdStr);
            long channelId = Long.parseUnsignedLong(channelIdStr);
            TempRoleManager.setRoleEnabled(roleId, channelId, enabled);
            return "OK";
        });

        get("/temp-roles-detailed", (req, res) -> {
            res.type("application/json");
            JDA jda = BotHolder.getJDA();
            var detailed = TempRoleManager.getTempRoles().stream().map(r -> {
                RoleDetailed d = new RoleDetailed();
                d.roleId = String.valueOf(r.roleId);
                d.channelId = String.valueOf(r.channelId);
                Role role = jda.getRoleById(d.roleId);
                d.roleName = (jda != null && role != null)
                        ? role.getName()
                        : "(Unknown)";
                d.channelName = (jda != null && jda.getVoiceChannelById(r.channelId) != null)
                        ? jda.getVoiceChannelById(r.channelId).getName()
                        : "(Unknown)";
                d.enabled = r.enabled;
                return d;
            }).collect(Collectors.toList());
            return new Gson().toJson(detailed);
        });

        get("/debug-status", (req, res) -> BotSettings.isDebug() ? "enabled" : "disabled");
        post("/toggle-debug", (req, res) -> {
            boolean state = Boolean.parseBoolean(req.queryParams("status"));
            BotSettings.setDebug(state);
            res.redirect("/");
            System.out.println("[dashboard] Debug mode toggled to: " + (state ? "ON" : "OFF"));
            return "Debug mode toggled to: " + (state ? "ON" : "OFF");
        });

        post("/add-binding", (req, res) -> {
            String userIdStr = req.queryParams("userId");
            String channelIdStr = req.queryParams("channelId");
            long userId = Long.parseUnsignedLong(userIdStr);
            long channelId = Long.parseUnsignedLong(channelIdStr);
            WywozBindingManager.addBinding(userId, channelId);
            return "OK";
        });

        post("/remove-binding", (req, res) -> {
            long userId = Long.parseUnsignedLong(req.queryParams("userId"));
            long channelId = Long.parseUnsignedLong(req.queryParams("channelId"));
            WywozBindingManager.removeBinding(userId, channelId);
            return "OK";
        });

        get("/bindings-detailed", (req, res) -> {
            res.type("application/json");
            JDA jda = BotHolder.getJDA();
            var detailed = WywozBindingManager.getBindings().stream().map(b -> {
                BindingDetailed d = new BindingDetailed();
                d.userId = String.valueOf(b.userId);
                d.channelId = String.valueOf(b.channelId);
                d.userName = (jda != null && jda.getUserById(b.userId) != null)
                        ? jda.getUserById(b.userId).getName()
                        : "(Unknown)";
                d.channelName = (jda != null && jda.getVoiceChannelById(b.channelId) != null)
                        ? jda.getVoiceChannelById(b.channelId).getName()
                        : "(Unknown)";
                d.enabled = b.enabled;
                return d;
            }).collect(Collectors.toList());
            return new Gson().toJson(detailed);
        });

        post("/toggle-binding", (req, res) -> {
            String userIdStr = req.queryParams("userId");
            String channelIdStr = req.queryParams("channelId");
            boolean enabled = Boolean.parseBoolean(req.queryParams("enabled"));
            long userId = Long.parseUnsignedLong(userIdStr);
            long channelId = Long.parseUnsignedLong(channelIdStr);
            WywozBindingManager.setBindingEnabled(userId, channelId, enabled);
            return "OK";
        });

        get("/current-track", (req, res) -> {
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
            } else {
                data.put("playing", false);
            }

            return data;
        }, gson::toJson);

        get("/queue", (req, res) -> {
            res.type("application/json");
            Map<String, Object> data = new HashMap<>();

            if (DiscordBot.musicBot != null) {
                var queue = new java.util.ArrayList<Map<String, Object>>();
                int index = 0;
                for (var track : DiscordBot.musicBot.trackQueue) {
                    Map<String, Object> trackData = new HashMap<>();
                    trackData.put("index", index++);
                    trackData.put("title", track.getInfo().title);
                    trackData.put("author", track.getInfo().author);
                    trackData.put("uri", track.getInfo().uri);
                    trackData.put("duration", track.getDuration());
                    trackData.put("artworkUrl", track.getInfo().artworkUrl);
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
        }, gson::toJson);

        get("/bot-status", (req, res) -> {
            res.type("application/json");
            Map<String, Object> data = new HashMap<>();

            JDA jda = BotHolder.getJDA();
            boolean isConnected = false;
            String channelName = null;

            if (jda != null) {
                for (Guild guild : jda.getGuilds()) {
                    if (guild.getAudioManager().isConnected()) {
                        isConnected = true;
                        var channel = guild.getAudioManager().getConnectedChannel();
                        if (channel != null) {
                            channelName = channel.getName();
                        }
                        break;
                    }
                }
            }

            data.put("inVoiceChannel", isConnected);
            data.put("channelName", channelName);

            return data;
        }, gson::toJson);

        post("/music/play-pause", (req, res) -> {
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
        }, gson::toJson);

        post("/music/skip", (req, res) -> {
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
        }, gson::toJson);

        post("/music/stop", (req, res) -> {
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
        }, gson::toJson);

        post("/music/shuffle", (req, res) -> {
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
        }, gson::toJson);

        post("/music/loop", (req, res) -> {
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
        }, gson::toJson);

        post("/music/clear", (req, res) -> {
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
        }, gson::toJson);

        post("/music/remove", (req, res) -> {
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

                var track = queue.get(position);
                DiscordBot.musicBot.trackQueue.remove(track);
                result.put("success", true);
                result.put("removed", track.getInfo().title);
                System.out.println(DiscordBot.getTimestamp() + "[web-player] Removed track at position " + position + ": " + track.getInfo().title);
            } catch (Exception e) {
                result.put("success", false);
                result.put("message", e.getMessage());
                System.out.println(DiscordBot.getTimestamp() + "[web-player] Remove track failed: " + e.getMessage());
            }

            return result;
        }, gson::toJson);

        post("/music/add", (req, res) -> {
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

                result.put("success", true);
                result.put("message", "Loading track...");
                result.put("url", url);
                System.out.println(DiscordBot.getTimestamp() + "[web-player] Loading track: " + url);

                final String finalUrl = url;
                new Thread(() -> {
                    DiscordBot.musicBot.playerManager.loadItem(finalUrl, new com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler() {
                        @Override
                        public void trackLoaded(com.sedmelluq.discord.lavaplayer.track.AudioTrack track) {
                            DiscordBot.musicBot.trackQueue.offerLast(track);
                            if (DiscordBot.musicBot.player.getPlayingTrack() == null) {
                                DiscordBot.musicBot.playNextTrack();
                            }
                            System.out.println(DiscordBot.getTimestamp() + "[web-player] Added track: " + track.getInfo().title);
                        }

                        @Override
                        public void playlistLoaded(com.sedmelluq.discord.lavaplayer.track.AudioPlaylist playlist) {
                            if (finalUrl.startsWith("ytsearch:")) {
                                if (playlist.getTracks().isEmpty()) {
                                    System.out.println(DiscordBot.getTimestamp() + "[web-player] No matches found for search: " + finalUrl);
                                    return;
                                }
                                com.sedmelluq.discord.lavaplayer.track.AudioTrack firstTrack = playlist.getTracks().get(0);
                                DiscordBot.musicBot.trackQueue.offerLast(firstTrack);
                                if (DiscordBot.musicBot.player.getPlayingTrack() == null) {
                                    DiscordBot.musicBot.playNextTrack();
                                }
                                System.out.println(DiscordBot.getTimestamp() + "[web-player] Added first search result: " + firstTrack.getInfo().title);
                            } else {
                                for (var track : playlist.getTracks()) {
                                    DiscordBot.musicBot.trackQueue.offerLast(track);
                                }
                                if (DiscordBot.musicBot.player.getPlayingTrack() == null) {
                                    DiscordBot.musicBot.playNextTrack();
                                }
                                System.out.println(DiscordBot.getTimestamp() + "[web-player] Added playlist: " + playlist.getName() + " (" + playlist.getTracks().size() + " tracks)");
                            }
                        }

                        @Override
                        public void noMatches() {
                            System.out.println(DiscordBot.getTimestamp() + "[web-player] No matches found for: " + finalUrl);
                        }

                        @Override
                        public void loadFailed(com.sedmelluq.discord.lavaplayer.tools.FriendlyException exception) {
                            System.out.println(DiscordBot.getTimestamp() + "[web-player] Failed to load track: " + exception.getMessage());
                        }
                    });
                }).start();

            } catch (Exception e) {
                result.put("success", false);
                result.put("message", e.getMessage());
                System.out.println(DiscordBot.getTimestamp() + "[web-player] Add track exception: " + e.getMessage());
            }

            return result;
        }, gson::toJson);

        post("/music/seek", (req, res) -> {
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
        }, gson::toJson);

        post("/music/reorder", (req, res) -> {
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

                var track = queue.remove(from);
                queue.add(to, track);

                DiscordBot.musicBot.trackQueue.clear();
                DiscordBot.musicBot.trackQueue.addAll(queue);

                result.put("success", true);
                System.out.println(DiscordBot.getTimestamp() + "[web-player] Reordered track: \"" + track.getInfo().title + "\" from position " + from + " to " + to);
            } catch (Exception e) {
                result.put("success", false);
                result.put("message", e.getMessage());
                System.out.println(DiscordBot.getTimestamp() + "[web-player] Reorder track exception: " + e.getMessage());
            }

            return result;
        }, gson::toJson);

        post("/force-restart", (req, res) -> {
            try {
                res.redirect("/");
                String processInfo = ProcessHandle.current().info().toString();
                DiscordBot.forceRestart();
                Thread.sleep(1000);
                if (ProcessHandle.current().info().toString().equals(processInfo)) {
                    res.status(500);
                    return "Restart failed - process did not change";
                }
                return "Restart successful";
            } catch (Exception e) {
                res.status(500);
                return "Error during restart: " + e.getMessage();
            }
        });
    }

    public static class BindingDetailed {
        public String userName;
        public String channelName;
        public String userId;
        public String channelId;
        public boolean enabled;
    }

    public static class RoleDetailed {
        public String roleName;
        public String roleId;
        public String channelName;
        public String channelId;
        public boolean enabled;
    }
}