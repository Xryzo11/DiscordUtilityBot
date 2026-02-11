package com.xryzo11.discordbot.core;

import com.xryzo11.discordbot.DiscordBot;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Properties;

public class Config {
    private static final String CONFIG_FILE_PATH = DiscordBot.configDirectory;
    private static final String CONFIG_FILE_NAME = "config.properties";
    private static final String CONFIG_FILE = CONFIG_FILE_PATH + CONFIG_FILE_NAME;
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

        String authType = getWebAuthType();
        if (authType.equals("password")) {
            String password = properties.getProperty("web.auth.password");
            if (password == null || password.equals("YOUR_PASSWORD_HERE")) {
                throw new IllegalStateException("Web auth password not configured in config.properties");
            }
            webPassword = sha512(password);
        }

    }

    private static void writeConfig(File newConfigFile,
                                    String botToken,
                                    String guildId,
                                    String webAuthType,
                                    String webAuthPassword,
                                    String discordClientId,
                                    String discordClientSecret,
                                    String discordRedirectUri,
                                    int webPort,
                                    boolean autoRestartEnabled,
                                    int autoRestartHour,
                                    boolean configUpdate,
                                    String audioGoogleOAuth2,
                                    boolean audioSavingEnabled,
                                    boolean audioBlockAsmr,
                                    String spotifyClientId,
                                    String spotifyClientSecret,
                                    boolean limitPlaylist,
                                    boolean autoKickEnabled,
                                    boolean autoKickAuto,
                                    boolean tempRoleEnabled,
                                    boolean tempRoleAuto,
                                    boolean debugEnabled) {
        try (FileWriter writer = new FileWriter(newConfigFile)) {
            writer.write("#################################\n");
            writer.write("# CORE SETTINGS\n");
            writer.write("#################################\n\n");

            writer.write("# Discord bot token (https://discord.com/developers/applications) [token]\n");
            writer.write("bot.token=" + botToken + "\n\n");

            writer.write("# Guild ID [id]\n");
            writer.write("guild.id=" + guildId + "\n\n");

            writer.write("# Web panel authentication type [none/password/discord]\n");
            writer.write("web.auth.type=" + webAuthType + "\n\n");

            writer.write("# Web panel password (requires web.auth.type=password) [string]\n");
            writer.write("web.auth.password=" + webAuthPassword + "\n\n");

            writer.write("# Discord OAuth2 Client ID (https://discord.com/developers/applications) (requires web.auth.type=discord) [string]\n");
            writer.write("discord.client.id=" + discordClientId + "\n\n");

            writer.write("# Discord OAuth2 Client Secret (https://discord.com/developers/applications) (requires web.auth.type=discord) [string]\n");
            writer.write("discord.client.secret=" + discordClientSecret + "\n\n");

            writer.write("# Discord OAuth2 Redirect URI (where to redirect after login) (requires web.auth.type=discord) [string]\n");
            writer.write("discord.redirect.uri=" + discordRedirectUri + "\n\n");

            writer.write("# Http web panel port [int]\n");
            writer.write("web.port=" + webPort + "\n\n");

            writer.write("# Enable automatic restarts (only when bot is unused) [true/false]\n");
            writer.write("auto.restart.enabled=" + autoRestartEnabled + "\n\n");

            writer.write("# Automatic restart hour (24-hour format, requires auto.restart.enabled) [int]h\n");
            writer.write("auto.restart.hour=" + autoRestartHour + "\n\n");

            writer.write("# Automatically update config by removing and creating a new one (with current settings), is highly recommended [true/false]\n");
            writer.write("config.update=" + configUpdate + "\n\n");

            writer.write("\n\n#################################\n");
            writer.write("# MUSIC BOT SETTINGS\n");
            writer.write("#################################\n\n");

            writer.write("# Use OAuth2 to access content on youtube [String]\n");
            writer.write("audio.google.oauth2=" + audioGoogleOAuth2 + "\n\n");

            writer.write("# Enable saving tracks for easier playback ('/save' and '/add' commands) [true/false]\n");
            writer.write("audio.saving.enabled=" + audioSavingEnabled + "\n\n");

            writer.write("# Block ASMR content [true/false]\n");
            writer.write("audio.block.asmr=" + audioBlockAsmr + "\n\n");

            writer.write("# Spotify Client ID (required for Spotify playlist support) [string]\n");
            writer.write("spotify.client.id=" + spotifyClientId + "\n\n");

            writer.write("# Spotify Client Secret (required for Spotify playlist support) [string]\n");
            writer.write("spotify.client.secret=" + spotifyClientSecret + "\n\n");

            writer.write("# Limit max Youtube/Spotify playlist size to 500 (doesn't limit queue size | false = infinite) [true/false]\n");
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
                configFile,
                "YOUR_BOT_TOKEN",
                "YOUR_GUILD_ID_HERE",
                "password",
                "YOUR_PASSWORD_HERE",
                "YOUR_DISCORD_CLIENT_ID_HERE",
                "YOUR_DISCORD_CLIENT_SECRET_HERE",
                "http://localhost:21379/auth/discord/callback",
                21379,
                true,
                6,
                true,
                "YOUR_OAUTH2_TOKEN_HERE",
                true,
                true,
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
        if (!configFile.exists()) {
            try {
                updateConfig(configFile);
            } catch (Exception e) {
                System.err.println("[config] Failed to create config file: " + e.getMessage());
                return;
            }
            System.out.println("[config] Config file updated successfully.");
        } else {
            File tempFile = new File(CONFIG_FILE + ".tmp");
            try {
                updateConfig(tempFile);
                if (!tempFile.exists()) {
                    System.err.println("[config] Failed to create temporary config file for update. Aborting.");
                    return;
                }
                configFile.delete();
                Files.copy(tempFile.toPath(), configFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                if (!configFile.exists()) {
                    System.err.println("[config] Failed to replace old config file with updated one. Aborting.");
                    return;
                }
                tempFile.delete();
                System.out.println("[config] Config file updated successfully.");
            } catch (IOException e) {
                System.out.println("[config] Failed to update config file: " + e.getMessage());
                return;
            }
        }
    }

    private static void updateConfig(File newFile) {
        writeConfig(
                newFile,
                getBotToken(),
                properties.getProperty("guild.id", "YOUR_GUILD_ID_HERE"),
                getWebAuthType(),
                properties.getProperty("web.auth.password", "YOUR_PASSWORD_HERE"),
                properties.getProperty("discord.client.id", "YOUR_DISCORD_CLIENT_ID_HERE"),
                properties.getProperty("discord.client.secret", "YOUR_DISCORD_CLIENT_SECRET_HERE"),
                properties.getProperty("discord.redirect.uri", "http://localhost:21379/auth/discord/callback"),
                getWebPort(),
                isAutoRestartEnabled(),
                getAutoRestartHour(),
                isConfigUpdateEnabled(),
                getGoogleOAuth2Token(),
                isAudioSavingEnabled(),
                isAsmrBlockEnabled(),
                getSpotifyClientId(),
                getSpotifyClientSecret(),
                isLimitPlaylistEnabled(),
                isAutoKickEnabled(),
                isAutoKickAutoEnabled(),
                isTempRoleEnabled(),
                isTempRoleAutoEnabled(),
                isDebugEnabled()
        );
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

    public static String getGuildId() {
        String guildId = properties.getProperty("guild.id");
        if (guildId == null || guildId.equals("YOUR_GUILD_ID_HERE")) {
            throw new IllegalStateException("Guild ID not configured in config.properties");
        }
        return guildId;
    }

    public static void validateGuildId() {
        String guildId = getGuildId();
        net.dv8tion.jda.api.JDA jda = BotHolder.getJDA();
        if (jda != null) {
            net.dv8tion.jda.api.entities.Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                throw new IllegalStateException("Bot is not in the guild with ID: " + guildId);
            }
        }
    }

    public static String getWebAuthType() {
        String authType = properties.getProperty("web.auth.type", "password");
        if (!authType.equals("none") && !authType.equals("password") && !authType.equals("discord")) {
            throw new IllegalStateException("Invalid web.auth.type in config.properties. Must be 'none', 'password', or 'discord'");
        }
        return authType;
    }

    public static String getWebAuthPassword() {
        String password = properties.getProperty("web.auth.password");
        String authType = getWebAuthType();
        if (authType.equals("password")) {
            if (password == null || password.equals("YOUR_PASSWORD_HERE")) {
                throw new IllegalStateException("Web auth password not configured in config.properties");
            }
        }
        return webPassword;
    }

    public static int getWebPort() {
        return Integer.parseInt(properties.getProperty("web.port", "21379"));
    }

    public static String getDiscordClientId() {
        if (getWebAuthType().equals("discord")) {
            String clientId = properties.getProperty("discord.client.id");
            if (clientId == null || clientId.equals("YOUR_DISCORD_CLIENT_ID_HERE")) {
                throw new IllegalStateException("Discord OAuth2 Client ID not configured in config.properties");
            }
        }
        return properties.getProperty("discord.client.id", "YOUR_DISCORD_CLIENT_ID_HERE");
    }

    public static String getDiscordClientSecret() {
        if (getWebAuthType().equals("discord")) {
            String clientSecret = properties.getProperty("discord.client.secret");
            if (clientSecret == null || clientSecret.equals("YOUR_DISCORD_CLIENT_SECRET_HERE")) {
                throw new IllegalStateException("Discord OAuth2 Client Secret not configured in config.properties");
            }
        }
        return properties.getProperty("discord.client.secret", "YOUR_DISCORD_CLIENT_SECRET_HERE");
    }

    public static String getDiscordRedirectUri() {
        return properties.getProperty("discord.redirect.uri", "http://localhost:21379/auth/discord/callback");
    }

    public static String getGoogleOAuth2Token() {
        return properties.getProperty("audio.google.oauth2", "YOUR_OAUTH2_TOKEN_HERE");
    }

    public static boolean isAudioSavingEnabled() {
        return Boolean.parseBoolean(properties.getProperty("audio.saving.enabled", "true"));
    }

    public static boolean isAsmrBlockEnabled() {
        return Boolean.parseBoolean(properties.getProperty("audio.block.asmr", "false"));
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

    public static String getSpotifyClientId() {
        return properties.getProperty("spotify.client.id", "YOUR_SPOTIFY_CLIENT_ID_HERE");
    }

    public static String getSpotifyClientSecret() {
        return properties.getProperty("spotify.client.secret", "YOUR_SPOTIFY_CLIENT_SECRET_HERE");
    }

    public static boolean isLimitPlaylistEnabled() {
        return Boolean.parseBoolean(properties.getProperty("audio.limit.playlist", "true"));
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