package com.xryzo11.discordbot;

import java.io.*;
import java.util.Properties;

public class Config {
    private static final String CONFIG_FILE = "config.properties";
    private static Properties properties;

    static {
        properties = new Properties();
        File configFile = new File(CONFIG_FILE);

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
                writer.write("audio.directory=/tmp/discord_audio\n");
                writer.write("# Enable pre-loaded tracks [true/false]\n");
                writer.write("audio.preloaded=true\n");
                writer.write("# Pre-loaded directory [string]\n");
                writer.write("audio.preloaded.directory=/tmp/discord_audio_preloaded\n");
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
                writer.write("auto.restart.hour=5\n");
            } catch (IOException e) {
                System.err.println("Failed to create config file: " + e.getMessage());
            }
        }

        try (FileInputStream input = new FileInputStream(CONFIG_FILE)) {
            properties.load(input);
        } catch (IOException e) {
            System.err.println("Failed to load config: " + e.getMessage());
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
        return password;
    }

    public static int getWebPort() {
        return Integer.parseInt(properties.getProperty("web.port", "21379"));
    }

    public static int getAudioPort() {
        return Integer.parseInt(properties.getProperty("audio.port", "21378"));
    }

    public static String getAudioDirectory() {
        String dir = properties.getProperty("audio.directory", "/tmp/discord_audio");
        if (dir.isEmpty()) {
            throw new IllegalStateException("Audio directory not configured in config.properties");
        }
        return dir;
    }

    public static boolean isPreloadedEnabled() {
        return Boolean.parseBoolean(properties.getProperty("audio.preloaded", "true"));
    }

    public static String getPreloadedDirectory() {
        String dir = properties.getProperty("audio.preloaded.directory", "/tmp/discord_audio_preloaded");
        if (dir.isEmpty()) {
            throw new IllegalStateException("Pre-loaded directory not configured in config.properties");
        }
        return dir;
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
}