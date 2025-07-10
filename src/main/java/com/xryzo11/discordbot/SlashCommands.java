package com.xryzo11.discordbot;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SlashCommands {
    public static class SlashCommandInteractionEventListener extends ListenerAdapter {
        private final MusicBot bot;
        private final ExecutorService commandExecutor = Executors.newCachedThreadPool();

        public SlashCommandInteractionEventListener(MusicBot bot) {
            this.bot = bot;
        }

        @Override
        public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
            commandExecutor.submit(() -> handleCommand(event));
        }

        private void handleCommand(SlashCommandInteractionEvent event) {
            switch(event.getName()) {
                case "join":
                    handleJoinCommand(event);
                    break;
                case "queue":
                    handleQueueCommand(event);
                    break;
                case "pause":
                    bot.pausePlayer();
                    event.reply("⏸️ Playback paused").queue();
                    break;
                case "resume":
                    bot.resumePlayer();
                    event.reply("▶️ Playback resumed").queue();
                    break;
                case "clear":
                    bot.clearQueue();
                    event.reply("🧹 Queue cleared").queue();
                    break;
                case "stop":
                    bot.stopPlayer();
                    event.reply("⏹️ Playback stopped and disconnected").queue();
                    break;
                case "list":
                    handleListCommand(event);
                    break;
                case "skip":
                    handleSkipCommand(event);
                    break;
                case "loop":
                    handleLoopCommand(event);
                    break;
            }
        }

        private void handleJoinCommand(SlashCommandInteractionEvent event) {
            Guild guild = event.getGuild();
            Member member = event.getMember();

            if (member == null || !member.getVoiceState().inAudioChannel()) {
                event.reply("❌ You must be in a voice channel!").setEphemeral(true).queue();
                return;
            }

            VoiceChannel voiceChannel = member.getVoiceState().getChannel().asVoiceChannel();

            ensureVoiceConnection(guild, voiceChannel);
            bot.resumePlayer();
            bot.disableLoop();
            event.reply("🔊 Joined voice channel: " + voiceChannel.getName()).queue();
        }

        private void handleQueueCommand(SlashCommandInteractionEvent event) {
            String url = event.getOption("url").getAsString();
            Guild guild = event.getGuild();
            Member member = event.getMember();

            if (member == null || !member.getVoiceState().inAudioChannel()) {
                event.reply("❌ You must be in a voice channel!").setEphemeral(true).queue();
                return;
            }

            if (!guild.getAudioManager().isConnected()) {
                event.reply("❌ Bot is not in a voice channel! Use /join first").setEphemeral(true).queue();
                return;
            }

            if (url.contains("radio") || url.contains("stream") || url.contains("live")) {
                event.reply("❌ Radio or stream URLs are not supported").setEphemeral(true).queue();
                return;
            }

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

                            hook.editOriginal("🔄 Adding " + tracks.size() + " tracks to queue...").queue();
                            StringBuilder failedTracks = new StringBuilder();
                            int addedTracks = 0;

                            for (AudioTrackInfo trackInfo : tracks) {
                                try {
                                    String videoUrl = "https://youtube.com/watch?v=" + trackInfo.identifier;
                                    AudioTrackInfo processedTrack = AudioProcessor.processYouTubeAudio(videoUrl).get();

                                    bot.playerManager.loadItem(processedTrack.uri, new AudioLoadResultHandler() {
                                        @Override
                                        public void trackLoaded(AudioTrack track) {
                                            track.setUserData(processedTrack.title);
                                            bot.trackQueue.offer(track);
                                            if (bot.player.getPlayingTrack() == null) {
                                                bot.playNextTrack();
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

                                    if (addedTracks % 5 == 0) {
                                        hook.editOriginal("🔄 Added " + addedTracks + "/" + tracks.size() + " tracks...").queue();
                                    }
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
                            hook.editOriginal("📥 Downloading audio...").queue();

                            AudioTrackInfo trackInfo = AudioProcessor.processYouTubeAudio(url).get();
                            File audioFile = new File(AudioProcessor.AUDIO_DIR + trackInfo.identifier + ".mp3");

                            if (!audioFile.exists() || !audioFile.canRead()) {
                                hook.editOriginal("❌ Audio file not ready. Please try again.").queue();
                                return;
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

                            bot.playerManager.loadItem(trackInfo.uri, new AudioLoadResultHandler() {
                                @Override
                                public void trackLoaded(AudioTrack track) {
                                    track.setUserData(trackInfo.title);
                                    bot.trackQueue.offer(track);

                                    String message = bot.player.getPlayingTrack() == null ?
                                            "✅ Added and playing: " + trackInfo.title :
                                            "✅ Added to queue: " + trackInfo.title;

                                    hook.editOriginal(message).queue();

                                    if (bot.player.getPlayingTrack() == null) {
                                        bot.playNextTrack();
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

        private void handleListCommand(SlashCommandInteractionEvent event) {
            String queueList = bot.getQueueList();
            event.reply(queueList).queue();
        }

        private void ensureVoiceConnection(Guild guild, VoiceChannel channel) {
            AudioManager audioManager = guild.getAudioManager();

            if (audioManager.isConnected() && audioManager.getConnectedChannel().equals(channel)) {
                return;
            }

            if (audioManager.isConnected()) {
                audioManager.closeAudioConnection();
            }

            audioManager.openAudioConnection(channel);

            if (audioManager.getSendingHandler() == null) {
                audioManager.setSendingHandler(new LavaPlayerAudioSendHandler(bot.player));
            }
        }

        private void handleSkipCommand(SlashCommandInteractionEvent event) {
            if (bot.player.getPlayingTrack() == null) {
                event.reply("❌ No track is currently playing").setEphemeral(true).queue();
                return;
            }

            String title = bot.currentTrack.getInfo().title;
            bot.skipCurrentTrack();
            event.reply("⏭️ Skipped: " + title).queue();
        }

        private void handleLoopCommand(SlashCommandInteractionEvent event) {
            bot.toggleLoop();
            String status = bot.isLoopEnabled() ? "enabled" : "disabled";
            event.reply("🔁 Queue loop " + status).queue();
        }
    }
}
