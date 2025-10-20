package com.xryzo11.discordbot.musicBot;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.xryzo11.discordbot.core.BotSettings;
import com.xryzo11.discordbot.core.Config;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.regex.*;

public class AudioProcessor {
    public static final String AUDIO_DIR = Config.getAudioDirectory();
    private static final int HTTP_PORT = Config.getAudioPort();
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final ConcurrentMap<String, CompletableFuture<AudioTrackInfo>> pendingDownloads = new ConcurrentHashMap<>();
    private static final Set<String> activeDownloads = ConcurrentHashMap.newKeySet();
    private static final Set<String> downloadedFiles = ConcurrentHashMap.newKeySet();

    static {
        File dir = new File(AUDIO_DIR);
        if (dir.exists()) {
            for (File file : dir.listFiles()) {
                if (file.isFile() && file.getName().endsWith(".webm")) {
                    downloadedFiles.add(file.getName().replace(".webm", ""));
                }
            }
        }
    }

    public static CompletableFuture<AudioTrackInfo> processYouTubeAudio(String youtubeUrl, boolean isUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String videoId = extractVideoId(youtubeUrl);
                String outputFile = AUDIO_DIR + videoId + ".webm";
                String httpUrl = "http://localhost:" + HTTP_PORT + "/audio/" + videoId + ".webm";
                File audioFile = new File(outputFile);
                File infoFile = new File(AUDIO_DIR + videoId + ".info.json");

                if (audioFile.exists() && audioFile.length() > 0 && infoFile.exists()) {
                    return loadMetadataFromJson(videoId, httpUrl);
                }

                if (activeDownloads.add(videoId)) {
                    try {
                        return downloadAndConvertWithMetadata(youtubeUrl, outputFile, isUrl);
                    } finally {
                        activeDownloads.remove(videoId);
                    }
                } else {
                    long startTime = System.currentTimeMillis();
                    while (activeDownloads.contains(videoId)) {
                        if (System.currentTimeMillis() - startTime > 30000) {
                            throw new TimeoutException("Download is taking too long");
                        }
                        Thread.sleep(100);
                    }
                    if (audioFile.exists() && audioFile.length() > 0 && infoFile.exists()) {
                        return loadMetadataFromJson(videoId, httpUrl);
                    } else {
                        throw new IOException("Audio file not available after waiting");
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to process audio: " + e.getMessage(), e);
            }
        }, executor);
    }

    private static AudioTrackInfo downloadAndConvertWithMetadata(String youtubeUrl, String outputFile, boolean isUrl) throws Exception {
        int maxRetries = 3;
        int retryCount = 0;
        Exception lastException = null;

        do {
            try {
                List<String> command = new ArrayList<>();
                command.add("yt-dlp");
                command.add("--format");
                command.add("bestaudio[ext=webm]/bestaudio");
                if (isUrl) {
                    command.add("-o");
                    command.add(outputFile);
                } else {
                    command.add("-o");
                    command.add(AUDIO_DIR + "%(id)s.%(ext)s");
                }
                command.add("--write-info-json");
                command.add("--print-json");
                command.add("--quiet");
                command.add("--no-warnings");
                command.add("--force-ipv4");
                command.add("--no-check-certificate");
                if (Config.isYtCookiesEnabled()) {
                    command.add("--cookies-from-browser");
                    command.add(Config.getYtCookiesBrowser());
                }
                if (isUrl) {
                    command.add(youtubeUrl);
                } else {
                    command.add("ytsearch1:" + youtubeUrl);
                }
                ProcessBuilder processBuilder = new ProcessBuilder(command);

                processBuilder.redirectErrorStream(true);
                Process process = processBuilder.start();

                StringBuilder jsonOutput = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.trim().startsWith("{") && line.contains("\"title\"")) {
                            jsonOutput.append(line).append("\n");
                        }
//                        if (BotSettings.isDebug()) {
//                            System.out.println("[yt-dlp] " + line);
//                        }
                    }
                }

                if (!process.waitFor(2, TimeUnit.MINUTES)) {
                    process.destroyForcibly();
                    throw new IOException("Download timed out");
                }
                if (process.exitValue() != 0) {
                    throw new IOException("yt-dlp failed with exit code " + process.exitValue());
                }

                JsonObject json = new Gson().fromJson(jsonOutput.toString(), JsonObject.class);
                String title = json.get("title").getAsString();
                long durationMs = json.has("duration") ? json.get("duration").getAsLong() * 1000 : 0;
                String videoId = json.get("id").getAsString();
                String httpUrl = "http://localhost:" + HTTP_PORT + "/audio/" + videoId + ".webm";

                return new AudioTrackInfo(title, "YouTube", durationMs, videoId, false, httpUrl);

            } catch (Exception e) {
                lastException = e;
                if (BotSettings.isDebug()) {
                    System.err.println("Download attempt " + (retryCount + 1) + " failed: " + e.getMessage());
                }
                Thread.sleep(1000 * (retryCount + 1));
            }
        } while (++retryCount < maxRetries);

        throw new IOException("Download failed after " + maxRetries + " attempts", lastException);
    }

    public static String extractVideoId(String url) {
        String pattern = "(?<=watch\\?v=|/videos/|embed\\/|youtu.be\\/|\\/v\\/|\\/e\\/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%\u200C\u200B2F|youtu.be%2F|\\/v%2F)[^#\\&\\?\\n]*";
        Pattern compiledPattern = Pattern.compile(pattern);
        Matcher matcher = compiledPattern.matcher(url);
        return matcher.find() ? matcher.group() : "unknown_" + System.currentTimeMillis();
    }

    public static void startHttpServer() {
        executor.submit(() -> {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
                server.setExecutor(Executors.newCachedThreadPool());

                server.createContext("/audio", exchange -> {
                    String filePath = AUDIO_DIR + exchange.getRequestURI().getPath().substring(7);
                    File file = new File(filePath);

                    if (!file.exists()) {
                        sendError(exchange, 404, "File not found");
                        return;
                    }

                    exchange.getResponseHeaders().set("Content-Type", "audio/mpeg");
                    exchange.getResponseHeaders().set("Transfer-Encoding", "chunked");
                    exchange.sendResponseHeaders(200, 0);

                    try (OutputStream os = exchange.getResponseBody();
                         BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file), 8192)) {

                        byte[] buffer = new byte[8192];
                        int bytesRead;

                        while ((bytesRead = bis.read(buffer)) != -1) {
                            try {
                                os.write(buffer, 0, bytesRead);
                                os.flush();
                            } catch (IOException e) {
                                if (BotSettings.isDebug() && !e.getMessage().contains("Broken pipe")) {
                                    System.out.println("[AudioProcessor-http] Client disconnected: " + e.getMessage());
                                }
                                break;
                            }
                        }
                    } catch (IOException e) {
                        if (BotSettings.isDebug() &&
                                !e.getMessage().contains("Broken pipe") &&
                                !e.getMessage().contains("Connection reset")) {
                            System.err.println("[AudioProcessor-http] Error streaming file: " + e.getMessage());
                        }
                    } finally {
                        exchange.close();
                    }
                });

                server.start();
                System.out.println("[AudioProcessor-http] HTTP server running on port " + HTTP_PORT);
            } catch (IOException e) {
                System.err.println("[AudioProcessor-http] Failed to start HTTP server: " + e.getMessage());
            }
        });
    }
    private static void sendError(HttpExchange exchange, int code, String message) throws IOException {
        exchange.sendResponseHeaders(code, message.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(message.getBytes());
        }
    }

    public static void cleanupAudioDirectory() {
        File[] files = getFilesFromAudioDirectory();
        removeFiles(files);
    }

    public static File[] getFilesFromAudioDirectory() {
        File dir = new File(AUDIO_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File[] files = dir.listFiles();
        return files;
    }

    public static void removeFiles(File[] files) {
        if (files != null) {
            for (File file : files) {
                removeFile(file);
            }
        }
    }

    public static void removeFile(File file) {
        file.delete();
    }

    public static CompletableFuture<List<String>> processYouTubePlaylist(String youtubeUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder(
                        "yt-dlp",
                        "--flat-playlist",
                        "--dump-json",
                        youtubeUrl
                );

                Process process = processBuilder.start();
                List<String> videoUrls = new ArrayList<>();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        try {
                            JsonObject json = new Gson().fromJson(line, JsonObject.class);
                            String videoId = json.get("id").getAsString();
                            String videoUrl = "https://www.youtube.com/watch?v=" + videoId;

                            videoUrls.add(videoUrl);
                        } catch (Exception e) {
                            if (BotSettings.isDebug()) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                if (!process.waitFor(30, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    throw new TimeoutException("Playlist info extraction timed out");
                }

                return videoUrls;
            } catch (Exception e) {
                throw new RuntimeException("Failed to process playlist: " + e.getMessage(), e);
            }
        }, executor);
    }

    private static AudioTrackInfo loadMetadataFromJson(String videoId, String httpUrl) {
        File jsonFile = new File(AUDIO_DIR + videoId + ".info.json");
        if (jsonFile.exists()) {
            try (Reader r = new FileReader(jsonFile)) {
                JsonObject json = new Gson().fromJson(r, JsonObject.class);
                String title = json.get("title").getAsString();
                long durationMs = json.has("duration") ? json.get("duration").getAsLong() * 1000 : 0;
                return new AudioTrackInfo(title, "YouTube", durationMs, videoId, false, httpUrl);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new AudioTrackInfo("Unknown", "YouTube", 0, videoId, false, httpUrl);
    }

}