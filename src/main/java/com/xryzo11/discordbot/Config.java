package com.xryzo11.discordbot;

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
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write("# Discord Bot Configuration [token]\n");
                writer.write("bot.token=YOUR_BOT_TOKEN_HERE\n");

                writer.write("# Web panel requires authentication [true/false]\n");
                writer.write("web.auth.enabled=true\n");

                writer.write("# Web panel password [string]\n");
                writer.write("web.auth.password=YOUR_PASSWORD_HERE\n");

                writer.write("# Http web panel port [int]\n");
                writer.write("web.port=21379\n");

                writer.write("# Http audio player port [int]\n");
                writer.write("audio.port=21378\n");

                writer.write("# Audio directory [string]\n");
                writer.write("audio.directory=/tmp/discord_audio/\n");

                writer.write("# Enable pre-loaded tracks [true/false]\n");
                writer.write("audio.preloaded=true\n");

                writer.write("# Pre-loaded directory [string]\n");
                writer.write("audio.preloaded.directory=/tmp/discord_audio_preloaded/\n");

                writer.write("# Copy tracks from preloaded dir to audio dir on startup [true/false]\n");
                writer.write("audio.preloaded.copy=true\n");

                writer.write("# Block ASMR content [true/false]\n");
                writer.write("audio.block.asmr=false\n");

                writer.write("# Use browser cookies for age restricted/private content on YouTube [true/false]\n");
                writer.write("audio.yt.cookies=false\n");

                writer.write("# Which browser to use for cookies [brave/chrome/chromium/edge/firefox/opera/safari/vivaldi/whale]\n");
                writer.write("audio.yt.cookies.browser=chrome\n");

                writer.write("# Enable auto-kick automatically [true/false]\n");
                writer.write("auto.kick.enabled=true\n");

                writer.write("# Enable debug automatically [true/false]\n");
                writer.write("debug.enabled=false\n");

                writer.write("# Automatically update yt-dlp using pip [true/false]\n");
                writer.write("update.yt-dlp=true\n");

                writer.write("# Automatically remove audio files on startup [true/false]\n");
                writer.write("audio.cleanup=true\n");

                writer.write("# Enable automatic restarts (only when unused) [true/false]\n");
                writer.write("auto.restart.enabled=true\n");

                writer.write("# Automatic restart hour (24-hour format) [int]h\n");
                writer.write("auto.restart.hour=6\n");

                writer.write("# Automatically update config  by removing and creating a new one (with current settings) [true/false]\n");
                writer.write("config.update=true\n");
            } catch (IOException e) {
                System.err.println("Failed to create config file: " + e.getMessage());
            }
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

    public static boolean isDebugEnabled() {
        return Boolean.parseBoolean(properties.getProperty("debug.enabled", "false"));
    }

    public static boolean isYtDlpUpdateEnabled() {
        return Boolean.parseBoolean(properties.getProperty("update.yt-dlp", "true"));
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

    public static void updateConfig() {
        String token = getBotToken();
        boolean webAuth = isWebAuthEnabled();
        String password = properties.getProperty("web.auth.password");
        int webPort = getWebPort();
        int audioPort = getAudioPort();
        String audioDir = getAudioDirectory();
        boolean preloaded = isPreloadedEnabled();
        String preloadedDir = getPreloadedDirectory();
        boolean preloadedCopy = isPreloadedCopyEnabled();
        boolean asmrBlock = isAsmrBlockEnabled();
        boolean ytCookies = isYtCookiesEnabled();
        String ytCookiesBrowser = getYtCookiesBrowser();
        boolean autoKick = isAutoKickEnabled();
        boolean debug = isDebugEnabled();
        boolean ytDlpUpdate = isYtDlpUpdateEnabled();
        boolean audioCleanup = isAudioCleanupEnabled();
        boolean autoRestart = isAutoRestartEnabled();
        int autoRestartHour = getAutoRestartHour();
        boolean configUpdate = isConfigUpdateEnabled();
        System.out.println("[config] Updating config file...");
        configFile.delete();
        if (!configFile.exists()) {
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write("# Discord Bot Configuration [token]\n");
                writer.write("bot.token=" + token + "\n");
                System.out.println("[config] Bot token saved as: " + token);

                writer.write("# Web panel requires authentication [true/false]\n");
                writer.write("web.auth.enabled=" + webAuth + "\n");
                System.out.println("[config] Web auth enabled saved as: " + webAuth);

                writer.write("# Web panel password [string]\n");
                writer.write("web.auth.password=" + password + "\n");
                System.out.println("[config] Web auth password saved as: (sha512) " + sha512(password));

                writer.write("# Http web panel port [int]\n");
                writer.write("web.port=" + webPort + "\n");
                System.out.println("[config] Web port saved as: " + webPort);

                writer.write("# Http audio player port [int]\n");
                writer.write("audio.port=" + audioPort + "\n");
                System.out.println("[config] Audio port saved as: " + audioPort);

                writer.write("# Audio directory [string]\n");
                writer.write("audio.directory=" + audioDir + "\n");
                System.out.println("[config] Audio directory saved as: " + audioDir);

                writer.write("# Enable pre-loaded tracks [true/false]\n");
                writer.write("audio.preloaded=" + preloaded + "\n");
                System.out.println("[config] Preloaded enabled saved as: " + preloaded);

                writer.write("# Pre-loaded directory [string]\n");
                writer.write("audio.preloaded.directory=" + preloadedDir + "\n");
                System.out.println("[config] Preloaded directory saved as: " + preloadedDir);

                writer.write("# Copy tracks from preloaded dir to audio dir on startup [true/false]\n");
                writer.write("audio.preloaded.copy=" + preloadedCopy + "\n");
                System.out.println("[config] Preloaded copy enabled saved as: " + preloadedCopy);

                writer.write("# Block ASMR content [true/false]\n");
                writer.write("audio.block.asmr=" + asmrBlock + "\n");
                System.out.println("[config] ASMR block enabled saved as: " + asmrBlock);

                writer.write("# Use browser cookies for age restricted/private content on YouTube [true/false]\n");
                writer.write("audio.yt.cookies=" + ytCookies + "\n");
                System.out.println("[config] YouTube cookies enabled saved as: " + ytCookies);

                writer.write("# Which browser to use for cookies [brave/chrome/chromium/edge/firefox/opera/safari/vivaldi/whale]\n");
                writer.write("audio.yt.cookies.browser=" + ytCookiesBrowser + "\n");
                System.out.println("[config] YouTube cookies browser saved as: " + ytCookiesBrowser);

                writer.write("# Enable auto-kick automatically [true/false]\n");
                writer.write("auto.kick.enabled=" + autoKick + "\n");
                System.out.println("[config] Auto-kick enabled saved as: " + autoKick);

                writer.write("# Enable debug automatically [true/false]\n");
                writer.write("debug.enabled=" + debug + "\n");
                System.out.println("[config] Debug enabled saved as: " + debug);

                writer.write("# Automatically update yt-dlp using pip [true/false]\n");
                writer.write("update.yt-dlp=" + ytDlpUpdate + "\n");
                System.out.println("[config] yt-dlp update enabled saved as: " + ytDlpUpdate);

                writer.write("# Automatically remove audio files on startup [true/false]\n");
                writer.write("audio.cleanup=" + audioCleanup + "\n");
                System.out.println("[config] Audio cleanup enabled saved as: " + audioCleanup);

                writer.write("# Enable automatic restarts (only when unused) [true/false]\n");
                writer.write("auto.restart.enabled=" + autoRestart + "\n");
                System.out.println("[config] Auto-restart enabled saved as: " + autoRestart);

                writer.write("# Automatic restart hour (24-hour format) [int]h\n");
                writer.write("auto.restart.hour=" + autoRestartHour + "\n");
                System.out.println("[config] Auto-restart hour saved as: " + autoRestartHour);

                writer.write("# Automatically update config by removing and creating a new one (with current settings) [true/false]\n");
                writer.write("config.update=" + configUpdate + "\n");
                System.out.println("[config] Config update enabled saved as: " + configUpdate);

                System.out.println("[config] Config file updated.");
            }
            catch (IOException e) {
                System.err.println("Failed to create config file: " + e.getMessage());
            }
        } else {
            System.out.println("[config] Config file didn't remove. Not updating.");
        }
    }
}