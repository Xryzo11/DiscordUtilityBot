package com.xryzo11.discordbot;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

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

    public static CompletableFuture<AudioTrackInfo> processYouTubeAudio(String youtubeUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String videoId = extractVideoId(youtubeUrl);
                String outputFile = AUDIO_DIR + videoId + ".webm";
                String httpUrl = "http://localhost:" + HTTP_PORT + "/audio/" + videoId + ".webm";

                File file = new File(outputFile);
                if (file.exists() && file.length() > 0) {
                    String title = getYoutubeTitle(youtubeUrl);
                    long duration = getYoutubeDuration(youtubeUrl);
                    return new AudioTrackInfo(
                            title,
                            "YouTube",
                            duration,
                            videoId,
                            false,
                            httpUrl
                    );
                }

                if (activeDownloads.add(videoId)) {
                    downloadAndConvert(youtubeUrl, outputFile);
                    downloadedFiles.add(videoId);
                    activeDownloads.remove(videoId);
                } else {
                    long startTime = System.currentTimeMillis();
                    while (activeDownloads.contains(videoId)) {
                        if (System.currentTimeMillis() - startTime > 30000) {
                            throw new TimeoutException("Download is taking too long");
                        }
                        Thread.sleep(100);
                    }
                }

                String title = getYoutubeTitle(youtubeUrl);
                long duration = getYoutubeDuration(youtubeUrl);

                return new AudioTrackInfo(
                        title,
                        "YouTube",
                        duration,
                        videoId,
                        false,
                        httpUrl
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to process audio: " + e.getMessage(), e);
            }
        }, executor);
    }

    private static AudioTrackInfo createTrackInfo(String filePath, String videoId, String httpUrl) {
        try {
            String title = getTitleFromFile(filePath);
            long duration = getDuration(filePath);
            return new AudioTrackInfo(
                    title,
                    "YouTube",
                    duration,
                    videoId,
                    false,
                    httpUrl
            );
        } catch (Exception e) {
            return new AudioTrackInfo(
                    "Cached Track",
                    "YouTube",
                    0,
                    videoId,
                    false,
                    httpUrl
            );
        }
    }

    private static String getTitleFromFile(String filePath) {
        String name = new File(filePath).getName();
        return name.replace(".webm", "").replaceAll("%20", " ");
    }

    private static long getDuration(String filePath) {
        // Placeholder - real implementation would read audio metadata
        return 0;
    }

    private static String getYoutubeTitle(String url) throws IOException, InterruptedException {
        try {
            Process process = new ProcessBuilder(
                    "/usr/local/bin/yt-dlp",
                    "--get-title",
                    url
            ).start();

            if (process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream())
                )) {
                    String title = reader.readLine();
                    return title != null && !title.isEmpty() ? title : extractVideoId(url);
                }
            }
        } catch (Exception e) {
            return extractVideoId(url);
        }
        return extractVideoId(url);
    }

    private static long getYoutubeDuration(String url) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "/usr/local/bin/yt-dlp",
                "--get-duration",
                url
        ).start();

        if (process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            )) {
                String duration = reader.readLine();
                String[] parts = duration.split(":");
                long seconds = 0;
                if (parts.length == 3) {
                    seconds = Long.parseLong(parts[0]) * 3600
                            + Long.parseLong(parts[1]) * 60
                            + Long.parseLong(parts[2]);
                } else if (parts.length == 2) {
                    seconds = Long.parseLong(parts[0]) * 60
                            + Long.parseLong(parts[1]);
                } else if (parts.length == 1) {
                    seconds = Long.parseLong(parts[0]);
                }
                return seconds * 1000;
            }
        }
        return 0;
    }

    private static void downloadAndConvert(String youtubeUrl, String outputFile) throws Exception {
        int maxRetries = 3;
        int retryCount = 0;
        Exception lastException = null;

        do {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder(
                        "yt-dlp",
                        "--format", "bestaudio[ext=webm]/bestaudio",
                        "-o", outputFile,
                        "--newline",
                        "--progress",
                        "--no-colors",
                        "--verbose",
                        "--force-ipv4",
                        "--no-check-certificate",
                        youtubeUrl
                );

                processBuilder.redirectErrorStream(true);
                Process download = processBuilder.start();

                StringBuilder output = new StringBuilder();
                Thread progressReader = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(download.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            output.append(line).append("\n");
                            if (BotSettings.isDebug()) {
                                System.out.println("[yt-dlp] " + line.trim());
                            }
                        }
                    } catch (IOException e) {
                        if (BotSettings.isDebug()) {
                            System.err.println("Error reading yt-dlp output: " + e.getMessage());
                        }
                    }
                });
                progressReader.start();

                if (!download.waitFor(2, TimeUnit.MINUTES)) {
                    download.destroyForcibly();
                    throw new IOException("Download timed out");
                }

                if (download.exitValue() == 0) {
                    return;
                }

                throw new IOException("Download failed with code: " + download.exitValue() + "\nOutput: " + output);

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

    private static String extractVideoId(String url) {
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
                                if (BotSettings.isDebug()) {
                                    System.out.println("Client disconnected: " + e.getMessage());
                                }
                                break;
                            }
                        }
                    } catch (IOException e) {
                        if (BotSettings.isDebug() &&
                                !e.getMessage().contains("Broken pipe") &&
                                !e.getMessage().contains("Connection reset")) {
                            System.err.println("Error streaming file: " + e.getMessage());
                        }
                    } finally {
                        exchange.close();
                    }
                });

                server.start();
                System.out.println("HTTP server running on port " + HTTP_PORT);
            } catch (IOException e) {
                System.err.println("Failed to start HTTP server: " + e.getMessage());
            }
        });
    }
    private static void sendError(HttpExchange exchange, int code, String message) throws IOException {
        exchange.sendResponseHeaders(code, message.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(message.getBytes());
        }
    }

//    public static void startCleanupScheduler() {
//        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
//        scheduler.scheduleAtFixedRate(() -> {
//            cleanupAudioDirectory();
//        }, 1, 1, TimeUnit.HOURS);
//    }

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

    public static CompletableFuture<List<AudioTrackInfo>> processYouTubePlaylist(String youtubeUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder(
                        "yt-dlp",
                        "--flat-playlist",
                        "--dump-json",
                        youtubeUrl
                );

                Process process = processBuilder.start();
                List<AudioTrackInfo> tracks = new ArrayList<>();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        try {
                            JsonObject json = new Gson().fromJson(line, JsonObject.class);
                            String videoId = json.get("id").getAsString();
                            String title = json.get("title").getAsString();
                            long duration = json.has("duration") ? json.get("duration").getAsLong() * 1000 : 0;
                            String httpUrl = "http://localhost:" + HTTP_PORT + "/audio/" + videoId + ".webm";

                            tracks.add(new AudioTrackInfo(
                                    title,
                                    "YouTube",
                                    duration,
                                    videoId,
                                    false,
                                    httpUrl
                            ));
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

                return tracks;
            } catch (Exception e) {
                throw new RuntimeException("Failed to process playlist: " + e.getMessage(), e);
            }
        }, executor);
    }
}