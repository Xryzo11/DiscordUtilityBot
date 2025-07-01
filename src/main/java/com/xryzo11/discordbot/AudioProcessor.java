package com.xryzo11.discordbot;

import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.regex.*;

public class AudioProcessor {
    public static final String AUDIO_DIR = "/tmp/discord_audio/";
    private static final int HTTP_PORT = 21378;
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final ConcurrentMap<String, CompletableFuture<AudioTrackInfo>> pendingDownloads = new ConcurrentHashMap<>();
    private static final Map<String, AudioTrackInfo> trackCache = new ConcurrentHashMap<>();
    private static final Set<String> activeDownloads = ConcurrentHashMap.newKeySet();
    private static final Set<String> downloadedFiles = ConcurrentHashMap.newKeySet();

    static {
        File dir = new File(AUDIO_DIR);
        if (dir.exists()) {
            for (File file : dir.listFiles()) {
                if (file.isFile() && file.getName().endsWith(".mp3")) {
                    downloadedFiles.add(file.getName().replace(".mp3", ""));
                }
            }
        }
    }

    public static CompletableFuture<AudioTrackInfo> processYouTubeAudio(String youtubeUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String videoId = extractVideoId(youtubeUrl);
                String outputFile = AUDIO_DIR + videoId + ".mp3";
                String httpUrl = "http://localhost:" + HTTP_PORT + "/audio/" + videoId + ".mp3";

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
        return name.replace(".mp3", "").replaceAll("%20", " ");
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
        String tempFile = outputFile + ".part";
        Process download = new ProcessBuilder(
                "/usr/local/bin/yt-dlp",
                "-x",
                "--audio-format", "mp3",
                "--audio-quality", "192K",
                "--concurrent-fragments", "16",
                "--buffer-size", "128K",
                "--http-chunk-size", "10M",
                "--retries", "3",
                "--fragment-retries", "3",
                "--throttled-rate", "100M",
                "-o", tempFile,
                "--no-cache-dir",
                "--format", "bestaudio",
                youtubeUrl
        ).start();

        if (!download.waitFor(2, TimeUnit.MINUTES)) {
            download.destroyForcibly();
            throw new IOException("Download timed out");
        }

        if (download.exitValue() != 0) {
            throw new IOException("Download failed with code: " + download.exitValue());
        }

        Files.move(Paths.get(tempFile), Paths.get(outputFile), StandardCopyOption.REPLACE_EXISTING);
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

    public static void startCleanupScheduler() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            File dir = new File(AUDIO_DIR);
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000) {
                        file.delete();
                    }
                }
            }
        }, 1, 1, TimeUnit.HOURS);
    }
}