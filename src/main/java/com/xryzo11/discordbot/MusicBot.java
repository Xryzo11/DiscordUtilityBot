package com.xryzo11.discordbot;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class MusicBot {
    public final AudioPlayerManager playerManager;
    public static AudioPlayer player;
    public static final LinkedBlockingQueue<AudioTrack> trackQueue = new LinkedBlockingQueue<>();
    public AudioTrack currentTrack;
    private boolean loopEnabled = false;

    public MusicBot() {
        playerManager = new DefaultAudioPlayerManager();
        playerManager.registerSourceManager(new HttpAudioSourceManager());
        player = playerManager.createPlayer();
        playerManager.getConfiguration().setFilterHotSwapEnabled(true);

        AudioProcessor.startHttpServer();
        AudioProcessor.cleanupAudioDirectory();

        player.addListener(new AudioEventAdapter() {
            @Override
            public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
                currentTrack = null;
                if (endReason.mayStartNext) {
                    if (loopEnabled && track != null) {
                        trackQueue.offer(track.makeClone());
                    }
                    MusicBot.playNextTrack();
                }
            }

            @Override
            public void onTrackStart(AudioPlayer player, AudioTrack track) {
                currentTrack = track;
            }
        });
    }

    public void playTrack(AudioTrack track) {
        if (player.getPlayingTrack() == null) {
            player.playTrack(track);
        } else {
            trackQueue.offer(track);
        }
    }

    public static void playNextTrack() {
        AudioTrack nextTrack = trackQueue.poll();
        if (nextTrack != null) {
            player.playTrack(nextTrack);
        } else {
            player.stopTrack();
        }
    }

    public void pausePlayer() {
        player.setPaused(true);
    }

    public void resumePlayer() {
        player.setPaused(false);
    }

    public void clearQueue() {
        trackQueue.clear();
    }

    public void stopPlayer() {
        player.stopTrack();
        clearQueue();
        disconnectFromAllVoiceChannels();
    }

    static void disconnectFromAllVoiceChannels() {
        JDA jda = BotHolder.getJDA();
        if (jda != null) {
            for (Guild guild : jda.getGuilds()) {
                if (guild.getAudioManager().isConnected()) {
                    guild.getAudioManager().closeAudioConnection();
                }
            }
        }
    }

    public String getQueueList() {
        StringBuilder builder = new StringBuilder();

        builder.append("\uD83D\uDD01 **Looping:** ");
        if (loopEnabled) {
            builder.append("✅");
        } else {
            builder.append("❌");
        }
        builder.append("\n\n");

        if (currentTrack != null) {
            builder.append("▶️ **Now Playing:**\n")
                    .append(formatTrackInfo(currentTrack))
                    .append("\n\n");
        }

        if (trackQueue.isEmpty()) {
            builder.append("\uD83C\uDF10 Queue is empty");
        } else {
            builder.append("\uD83D\uDCCB **Queue:**\n");
            int index = 1;
            for (AudioTrack track : trackQueue) {
                builder.append(index++)
                        .append(". ")
                        .append(formatTrackInfo(track))
                        .append("\n");

                if (index > 10) {
                    builder.append("\n... and ")
                            .append(trackQueue.size() - 10)
                            .append(" more tracks");
                    break;
                }
            }
        }

        return builder.toString();
    }

    public void skipCurrentTrack() {
        if (player.getPlayingTrack() != null) {
            player.stopTrack();
            playNextTrack();
        }
    }

    private String formatTrackInfo(AudioTrack track) {
        AudioTrackInfo info = track.getInfo();
        long durationMs = track.getDuration();

        String uri = info.uri;
        String youtubeId = uri.substring(uri.lastIndexOf('/') + 1).replace(".webm", "");

        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60;
        String durationStr = String.format("%d:%02d", minutes, seconds);

        String youtubeUrl = "https://www.youtube.com/watch?v=" + youtubeId;
        String title = track.getUserData() != null ? track.getUserData().toString() : youtubeId;

        return String.format("[%s](<%s>) `[%s]`",
                title,
                youtubeUrl,
                durationStr);
    }

    public boolean isLoopEnabled() {
        return loopEnabled;
    }

    public void toggleLoop() {
        loopEnabled = !loopEnabled;
    }

    public void disableLoop() {
        loopEnabled = false;
    }

    public int movePlayhead(int position) {
        if (player.getPlayingTrack() != null) {
            player.getPlayingTrack().setPosition(position);
            return 0;
        }
        return 1;
    }

    public void shuffleQueue() {
        List<AudioTrack> trackList = new ArrayList<>(trackQueue);
        Collections.shuffle(trackList);
        trackQueue.clear();
        trackQueue.addAll(trackList);
    }

    public void queue(SlashCommandInteractionEvent event, String url, Guild guild, Member member) {
        event.deferReply().queue(hook -> {
            CompletableFuture.runAsync(() -> {
                try {
                    boolean isPlaylist = url.contains("playlist?list=") || url.contains("&list=");

                    if (isPlaylist) {
                        hook.editOriginal("📋 Processing playlist...").queue();
                        List<AudioTrackInfo> tracks = AudioProcessor.processYouTubePlaylist(url).get();

                        if (tracks.isEmpty()) {
                            hook.editOriginal("❌ No tracks found in playlist").queue();
                            return;
                        }

                        if (tracks.size() > 250) {
                            hook.editOriginal("❗ Playlist is too long! (Max 250 tracks)").queue();
                            return;
                        }

                        hook.editOriginal("🔄 Adding " + tracks.size() + " tracks to queue...").queue();
                        StringBuilder failedTracks = new StringBuilder();
                        int addedTracks = 0;

                        for (AudioTrackInfo trackInfo : tracks) {
                            try {
                                String videoUrl = "https://youtube.com/watch?v=" + trackInfo.identifier;
                                AudioTrackInfo processedTrack = AudioProcessor.processYouTubeAudio(videoUrl).get();

                                playerManager.loadItem(processedTrack.uri, new AudioLoadResultHandler() {
                                    @Override
                                    public void trackLoaded(AudioTrack track) {
                                        track.setUserData(processedTrack.title);
                                        trackQueue.offer(track);
                                        if (player.getPlayingTrack() == null) {
                                            playNextTrack();
                                        }
                                    }

                                    @Override
                                    public void playlistLoaded(AudioPlaylist playlist) {}

                                    @Override
                                    public void noMatches() {
                                        if (BotSettings.isDebug()) {
                                            System.out.println("No matches found for: " + trackInfo.title);
                                        }
                                    }

                                    @Override
                                    public void loadFailed(FriendlyException exception) {
                                        if (BotSettings.isDebug()) {
                                            System.out.println("Failed to load: " + trackInfo.title);
                                            exception.printStackTrace();
                                        }
                                    }
                                });
                                addedTracks++;
                                hook.editOriginal("🔄 Added " + addedTracks + "/" + tracks.size() + " tracks...").queue();
                            } catch (Exception e) {
                                failedTracks.append("\n- ").append(trackInfo.title);
                                if (BotSettings.isDebug()) {
                                    e.printStackTrace();
                                }
                            }
                        }

                        String response = "✅ Successfully added " + addedTracks + " tracks to queue";
                        if (failedTracks.length() > 0) {
                            response += "\n\n❌ Failed to add these tracks:" + failedTracks.toString();
                        }
                        hook.editOriginal(response).queue();

                    } else {
                        hook.editOriginal("⏳ Processing YouTube URL...").queue();

                        String videoId = AudioProcessor.extractVideoId(url);
                        File audioFile = new File(AudioProcessor.AUDIO_DIR + videoId + ".webm");
                        File infoFile = new File(AudioProcessor.AUDIO_DIR + videoId + ".info.json");

                        if (audioFile.exists() && audioFile.length() > 0 && infoFile.exists()) {
                            hook.editOriginal("🔍 Fetching metadata...").queue();
                        } else {
                            hook.editOriginal("📥 Downloading audio...").queue();
                        }

                        AudioTrackInfo trackInfo = AudioProcessor.processYouTubeAudio(url).get();

                        if (!audioFile.exists() || !audioFile.canRead()) {
                            hook.editOriginal("❌ Audio file not ready. Please try again.").queue();
                            return;
                        }

                        if (Config.isAsmrBlockEnabled()) {
                            if (trackInfo.title.contains("ASMR") || trackInfo.title.contains("F4A") || trackInfo.title.contains("F4M") || trackInfo.title.contains("F4F") || trackInfo.title.contains("M4A") || trackInfo.title.contains("M4M") || trackInfo.title.contains("M4F")) {
                                hook.editOriginal("❌ ASMR content is blocked.").queue();
                                return;
                            }
                        }

                        hook.editOriginal("🎵 Loading track into player...").queue();

                        int retries = 0;
                        while (retries < 10) {
                            try (FileInputStream fis = new FileInputStream(audioFile)) {
                                if (audioFile.length() > 0) break;
                            } catch (Exception ignored) {}
                            Thread.sleep(200);
                            retries++;
                        }

                        playerManager.loadItem(trackInfo.uri, new AudioLoadResultHandler() {
                            @Override
                            public void trackLoaded(AudioTrack track) {
                                track.setUserData(trackInfo.title);
                                trackQueue.offer(track);

                                String message = player.getPlayingTrack() == null ?
                                        "✅ Added and playing: " + trackInfo.title :
                                        "✅ Added to queue: " + trackInfo.title;

                                hook.editOriginal(message).queue();

                                if (player.getPlayingTrack() == null) {
                                    playNextTrack();
                                }
                            }

                            @Override
                            public void playlistLoaded(AudioPlaylist playlist) {}

                            @Override
                            public void noMatches() {
                                hook.editOriginal("❌ No matching audio found").queue();
                            }

                            @Override
                            public void loadFailed(FriendlyException exception) {
                                hook.editOriginal("❌ Failed to load track: " + exception.getMessage()).queue();
                            }
                        });
                    }
                } catch (Exception e) {
                    hook.editOriginal("❌ Error processing track: " + e.getMessage()).queue();
                    if (BotSettings.isDebug()) {
                        e.printStackTrace();
                    }
                }
            });
        });
    }
}
