package com.xryzo11.discordbot.musicBot;

import com.xryzo11.discordbot.DiscordBot;
import com.xryzo11.discordbot.core.BotSettings;
import com.xryzo11.discordbot.core.SlashCommands;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class SpotifyHandler {
    private static MusicBot bot = DiscordBot.musicBot;

    public static void handleSpotifyUrl(SlashCommandInteractionEvent event, String url, Guild guild, Member member) {
        event.getHook().editOriginal("⏳ Processing Spotify URL...").queue();
        if (BotSettings.isDebug())  System.out.println("[SpotifyHandler] Received Spotify URL: " + url + " from user: " + member.getEffectiveName() + " in guild: " + guild.getName());
        if (url.contains("spotify.com/track/")) {
            handleSpotifyTrack(event, url);
        } else if (url.contains("spotify.com/album/")) {
            event.reply("❗ Album support is not yet implemented.").setEphemeral(true).queue();
            return;
        } else if (url.contains("spotify.com/playlist/")) {
            event.reply("❗ Playlist support is not yet implemented.").setEphemeral(true).queue();
            return;
        } else {
            event.reply("❗ Unsupported Spotify URL. Please provide a track, album, or playlist URL.").setEphemeral(true).queue();
        }
    }

    private static void handleSpotifyTrack(SlashCommandInteractionEvent event, String url) {
        event.getHook().editOriginal("Processing Spotify track...").queue();
        if (BotSettings.isDebug()) System.out.println("[SpotifyHandler] Handling Spotify track URL: " + url);

        int maxRetries = 5;
        int retryCount = 0;
        while (retryCount < maxRetries) {
            try {
                String ytUrl = null;
                SlashCommands.safeDefer(event);
                List<String> command = new ArrayList<>();
                command.add("python3");
                command.add("-m");
                command.add("spotdl");
                command.add("url");
                command.add(url);
                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.redirectErrorStream(true);
                Process process = processBuilder.start();

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

                if (ytUrl == null || ytUrl.isEmpty()) {
                    event.getHook().editOriginal("❌ Failed to retrieve YouTube URL from Spotify track.").queue();
                    if (BotSettings.isDebug()) System.out.println("[SpotifyHandler] No YouTube URL found in spotdl output.");
                    return;
                }

                bot.queue(event.getHook(), ytUrl);
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

}
