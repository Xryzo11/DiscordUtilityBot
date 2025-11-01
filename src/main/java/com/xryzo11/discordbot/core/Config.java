package com.xryzo11.discordbot.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Properties;

public class Config {
    private static final String CONFIG_FILE = "config.properties";
    private static Properties properties;
    private static String webPassword = null;
    private static final File configFile;

    static {
        properties = new Properties();
        configFile = new File(CONFIG_FILE);

        if (!configFile.exists()) {
            createDefaultConfig();
        }

        try (FileInputStream input = new FileInputStream(CONFIG_FILE)) {
            properties.load(input);
        } catch (IOException e) {
            System.err.println("Failed to load config: " + e.getMessage());
        }

        if (isWebAuthEnabled()) {
            String password = properties.getProperty("web.auth.password");
            if (password == null || password.equals("YOUR_PASSWORD_HERE")) {
                throw new IllegalStateException("Web auth password not configured in config.properties");
            }
            webPassword = sha512(password);
        }

    }

    private static void writeConfig(String botToken,
                                    boolean webAuthEnabled,
                                    String webAuthPassword,
                                    int webPort,
                                    boolean autoRestartEnabled,
                                    int autoRestartHour,
                                    boolean configUpdate,
                                    int audioPort,
                                    String audioDirectory,
                                    boolean audioPreloaded,
                                    String audioPreloadedDirectory,
                                    boolean audioPreloadedCopy,
                                    boolean audioCleanup,
                                    boolean audioBlockAsmr,
                                    boolean audioYtCookies,
                                    String audioYtCookiesBrowser,
                                    boolean updateYtDlp,
                                    String spotifyClientId,
                                    String spotifyClientSecret,
                                    boolean limitPlaylist,
                                    boolean autoKickEnabled,
                                    boolean autoKickAuto,
                                    boolean tempRoleEnabled,
                                    boolean tempRoleAuto,
                                    boolean debugEnabled) {
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("#################################\n");
            writer.write("# CORE SETTINGS\n");
            writer.write("#################################\n\n");

            writer.write("# Discord bot token [token]\n");
            writer.write("bot.token=" + botToken + "\n\n");

            writer.write("# Web panel requires authentication [true/false]\n");
            writer.write("web.auth.enabled=" + webAuthEnabled + "\n\n");

            writer.write("# Web panel password (requires web.auth.enabled) [string]\n");
            writer.write("web.auth.password=" + webAuthPassword + "\n\n");

            writer.write("# Http web panel port [int]\n");
            writer.write("web.port=" + webPort + "\n\n");

            writer.write("# Enable automatic restarts (only when bot is unused) [true/false]\n");
            writer.write("auto.restart.enabled=" + autoRestartEnabled + "\n\n");

            writer.write("# Automatic restart hour (24-hour format, requires auto.restart.enabled) [int]h\n");
            writer.write("auto.restart.hour=" + autoRestartHour + "\n\n");

            writer.write("# Automatically update config by removing and creating a new one (with current settings) [true/false]\n");
            writer.write("config.update=" + configUpdate + "\n\n");

            writer.write("\n\n#################################\n");
            writer.write("# MUSIC BOT SETTINGS\n");
            writer.write("#################################\n\n");

            writer.write("# Http audio player port (doesn't have to be open) [int]\n");
            writer.write("audio.port=" + audioPort + "\n\n");

            writer.write("# Audio directory [string]\n");
            writer.write("audio.directory=" + audioDirectory + "\n\n");

            writer.write("# Enable pre-loaded tracks [true/false]\n");
            writer.write("audio.preloaded=" + audioPreloaded + "\n\n");

            writer.write("# Pre-loaded directory (requires audio.preloaded) [string]\n");
            writer.write("audio.preloaded.directory=" + audioPreloadedDirectory + "\n\n");

            writer.write("# Copy tracks from preloaded dir to audio dir on startup (faster playback, requires audio.preloaded) [true/false]\n");
            writer.write("audio.preloaded.copy=" + audioPreloadedCopy + "\n\n");

            writer.write("# Automatically remove non-preloaded audio files on startup [true/false]\n");
            writer.write("audio.cleanup=" + audioCleanup + "\n\n");

            writer.write("# Block ASMR content [true/false]\n");
            writer.write("audio.block.asmr=" + audioBlockAsmr + "\n\n");

            writer.write("# Use browser cookies for age restricted/private content on YouTube [true/false]\n");
            writer.write("audio.yt.cookies=" + audioYtCookies + "\n\n");

            writer.write("# Which browser to use for cookies (requires audio.yt.cookies) [brave/chrome/chromium/edge/firefox/opera/safari/vivaldi/whale]\n");
            writer.write("audio.yt.cookies.browser=" + audioYtCookiesBrowser + "\n\n");

            writer.write("# Automatically update yt-dlp using pip (only set to true if yt-dlp was installed using pip, uses 'yt-dlp -U' otherwise) [true/false]\n");
            writer.write("update.yt-dlp=" + updateYtDlp + "\n\n");

            writer.write("# Spotify Client ID (required for Spotify playlist support) [string]\n");
            writer.write("spotify.client.id=" + spotifyClientId + "\n\n");

            writer.write("# Spotify Client Secret (required for Spotify playlist support) [string]\n");
            writer.write("spotify.client.secret=" + spotifyClientSecret + "\n\n");

            writer.write("# Limit max Youtube/Spotify playlist size to 250 (doesn't limit queue size | false = infinite) [true/false]\n");
            writer.write("audio.limit.playlist=" + limitPlaylist + "\n\n");

            writer.write("\n\n#################################\n");
            writer.write("# MISC SETTINGS\n");
            writer.write("#################################\n\n");

            writer.write("# Enable auto-kick functionality [true/false]\n");
            writer.write("auto.kick.enabled=" + autoKickEnabled + "\n\n");

            writer.write("# Enable auto-kick automatically in web panel (requires auto.kick.enabled) [true/false]\n");
            writer.write("auto.kick.auto=" + autoKickAuto + "\n\n");

            writer.write("# Enable temporary role functionality [true/false]\n");
            writer.write("temp.role.enabled=" + tempRoleEnabled + "\n\n");

            writer.write("# Enable temporary role automatically in web panel (requires temp.role.enabled) [true/false]\n");
            writer.write("temp.role.auto=" + tempRoleAuto + "\n\n");

            writer.write("# Enable debug automatically in web panel [true/false]\n");
            writer.write("debug.enabled=" + debugEnabled + "\n\n");
        } catch (IOException e) {
            System.err.println("Failed to write config: " + e.getMessage());
        }
    }

    private static void createDefaultConfig() {
        writeConfig(
                "YOUR_BOT_TOKEN",
                true,
                "YOUR_PASSWORD_HERE",
                21379,
                true,
                6,
                true,
                21378,
                "/tmp/discord_audio/",
                true,
                "/tmp/discord_audio_preloaded/",
                true,
                true,
                false,
                false,
                "chrome",
                false,
                "YOUR_SPOTIFY_CLIENT_ID_HERE",
                "YOUR_SPOTIFY_CLIENT_SECRET_HERE",
                true,
                true,
                true,
                true,
                true,
                false
        );
    }

    public static void updateConfig() {
        System.out.println("[config] Updating config file...");
        configFile.delete();
        if (!configFile.exists()) {
            writeConfig(
                    getBotToken(),
                    isWebAuthEnabled(),
                    properties.getProperty("web.auth.password"),
                    getWebPort(),
                    isAutoRestartEnabled(),
                    getAutoRestartHour(),
                    isConfigUpdateEnabled(),
                    getAudioPort(),
                    getAudioDirectory(),
                    isPreloadedEnabled(),
                    getPreloadedDirectory(),
                    isPreloadedCopyEnabled(),
                    isAudioCleanupEnabled(),
                    isAsmrBlockEnabled(),
                    isYtCookiesEnabled(),
                    getYtCookiesBrowser(),
                    isYtDlpUpdateEnabled(),
                    getSpotifyClientId(),
                    getSpotifyClientSecret(),
                    isLimitPlaylistEnabled(),
                    isAutoKickEnabled(),
                    isAutoKickAutoEnabled(),
                    isTempRoleEnabled(),
                    isTempRoleAutoEnabled(),
                    isDebugEnabled()
            );
            System.out.println("[config] Config file updated successfully.");
        } else {
            System.out.println("[config] Config file didn't remove. Not updating.");
        }
    }

    public static String sha512(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String getBotToken() {
        String token = properties.getProperty("bot.token");
        if (token == null || token.equals("YOUR_BOT_TOKEN_HERE")) {
            throw new IllegalStateException("Bot token not configured in config.properties");
        }
        return token;
    }

    public static boolean isWebAuthEnabled() {
        return Boolean.parseBoolean(properties.getProperty("web.auth.enabled", "true"));
    }

    public static String getWebAuthPassword() {
        String password = properties.getProperty("web.auth.password");
        if (password == null || password.equals("YOUR_PASSWORD_HERE")) {
            throw new IllegalStateException("Web auth password not configured in config.properties");
        }
        return webPassword;
    }

    public static int getWebPort() {
        return Integer.parseInt(properties.getProperty("web.port", "21379"));
    }

    public static int getAudioPort() {
        return Integer.parseInt(properties.getProperty("audio.port", "21378"));
    }

    public static String getAudioDirectory() {
        String dir = properties.getProperty("audio.directory", "/tmp/discord_audio/");
        if (dir == null || dir.trim().isEmpty()) {
            throw new IllegalStateException("Audio directory not configured in config.properties");
        }
        if (!dir.endsWith(File.separator)) {
            dir += File.separator;
        }
        return dir;
    }

    public static boolean isPreloadedEnabled() {
        return Boolean.parseBoolean(properties.getProperty("audio.preloaded", "true"));
    }

    public static String getPreloadedDirectory() {
        String dir = properties.getProperty("audio.preloaded.directory", "/tmp/discord_audio_preloaded/");
        if (dir == null || dir.trim().isEmpty()) {
            throw new IllegalStateException("Pre-loaded directory not configured in config.properties");
        }
        if (!dir.endsWith(File.separator)) {
            dir += File.separator;
        }
        return dir;
    }

    public static boolean isPreloadedCopyEnabled() {
        return Boolean.parseBoolean(properties.getProperty("audio.preloaded.copy", "true"));
    }

    public static boolean isAsmrBlockEnabled() {
        return Boolean.parseBoolean(properties.getProperty("audio.block.asmr", "false"));
    }

    public static boolean isYtCookiesEnabled() {
        return Boolean.parseBoolean(properties.getProperty("audio.yt.cookies", "false"));
    }

    public static String getYtCookiesBrowser() {
        return properties.getProperty("audio.yt.cookies.browser", "chrome").toLowerCase();
    }

    public static boolean isAutoKickEnabled() {
        return Boolean.parseBoolean(properties.getProperty("auto.kick.enabled", "true"));
    }

    public static boolean isAutoKickAutoEnabled() {
        return Boolean.parseBoolean(properties.getProperty("auto.kick.auto", "true"));
    }

    public static boolean isTempRoleEnabled() {
        return Boolean.parseBoolean(properties.getProperty("temp.role.enabled", "true"));
    }

    public static boolean isTempRoleAutoEnabled() {
        return Boolean.parseBoolean(properties.getProperty("temp.role.auto", "true"));
    }

    public static boolean isDebugEnabled() {
        return Boolean.parseBoolean(properties.getProperty("debug.enabled", "false"));
    }

    public static boolean isYtDlpUpdateEnabled() {
        return Boolean.parseBoolean(properties.getProperty("update.yt-dlp", "false"));
    }

    public static String getSpotifyClientId() {
        return properties.getProperty("spotify.client.id", "YOUR_SPOTIFY_CLIENT_ID_HERE");
    }

    public static String getSpotifyClientSecret() {
        return properties.getProperty("spotify.client.secret", "YOUR_SPOTIFY_CLIENT_SECRET_HERE");
    }

    public static boolean isLimitPlaylistEnabled() {
        return Boolean.parseBoolean(properties.getProperty("audio.limit.playlist", "true"));
    }

    public static boolean isAudioCleanupEnabled() {
        return Boolean.parseBoolean(properties.getProperty("audio.cleanup", "true"));
    }

    public static boolean isAutoRestartEnabled() {
        return Boolean.parseBoolean(properties.getProperty("auto.restart.enabled", "true"));
    }

    public static int getAutoRestartHour() {
        return Integer.parseInt(properties.getProperty("auto.restart.hour", "5"));
    }

    public static boolean isConfigUpdateEnabled() {
        return Boolean.parseBoolean(properties.getProperty("config.update", "true"));
    }
}