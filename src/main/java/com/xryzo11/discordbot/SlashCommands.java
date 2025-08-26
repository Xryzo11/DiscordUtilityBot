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
                case "play":
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
                    bot.disableLoop();
                    bot.clearQueue();
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
                case "shuffle":
                    handleShuffleCommand(event);
                    break;
                case "playhead":
                    handlePlayheadCommand(event);
                    break;
                case "add":
                    handleAddCommand(event);
                    break;
                case "rps-challenge":
                    handleRpsChallengeCommand(event);
                    break;
                case "rps-choose":
                    handleRpsChooseCommand(event);
                    break;
                case "rps-cancel":
                    handleRpsCancelCommand(event);
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
            bot.clearQueue();
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
                event.reply("❌ Radio or stream URLs are not supported").queue();
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

            AudioTrack track = bot.currentTrack;
            String title = track.getUserData() != null ? track.getUserData().toString() : track.getInfo().identifier;
            bot.skipCurrentTrack();
            event.reply("⏭️ Skipped: " + title).queue();
        }

        private void handleLoopCommand(SlashCommandInteractionEvent event) {
            bot.toggleLoop();
            String status = bot.isLoopEnabled() ? "enabled" : "disabled";
            event.reply("🔁 Queue loop " + status).queue();
        }

        private void handleShuffleCommand(SlashCommandInteractionEvent event) {
            bot.shuffleQueue();
            event.reply("🔀 Queue shuffled").queue();
        }

        private void handlePlayheadCommand(SlashCommandInteractionEvent event) {
            Guild guild = event.getGuild();
            Member member = event.getMember();
            int hour = event.getOption("hour").getAsInt();
            int minute = event.getOption("minute").getAsInt();
            int second = event.getOption("second").getAsInt();
            int totalSeconds = hour * 3600 + minute * 60 + second;

            if (member == null || !member.getVoiceState().inAudioChannel()) {
                event.reply("❌ You must be in a voice channel!").setEphemeral(true).queue();
                return;
            }

            if (!guild.getAudioManager().isConnected()) {
                event.reply("❌ Bot is not in a voice channel! Use /join first").setEphemeral(true).queue();
                return;
            }

            if (bot.player.getPlayingTrack() == null) {
                event.reply("❌ No track is currently playing").setEphemeral(true).queue();
                return;
            }

            if (hour < 0 || minute < 0 || second < 0) {
                event.reply("❌ Position must be a positive integer").setEphemeral(true).queue();
                return;
            }

            if (totalSeconds > bot.player.getPlayingTrack().getDuration() / 1000) {
                event.reply("❌ Position exceeds track duration").setEphemeral(true).queue();
                return;
            }

            int exec = bot.movePlayhead(totalSeconds * 1000);
            if (exec == 0) {
                event.reply("⏩ Playhead moved").queue();
                return;
            }
            event.reply("❌ Could not set playhead position!").setEphemeral(true).queue();
        }

        private void handleAddCommand(SlashCommandInteractionEvent event) {
//            bot.addExisting();
//            event.reply("✅ Added existing track to queue").queue();
            event.reply(("Not implemented yet :upside_down:")).queue();
        }

       private void handleRpsChallengeCommand(SlashCommandInteractionEvent event) {
         if (RockPaperScissors.isActive) {
                event.reply("❌ A Rock-Paper-Scissors game is already in progress").queue();
                return;
         }
         event.reply("🔄 Setting up game...").setEphemeral(true).queue();
         RockPaperScissors.challenge(event.getMember(), event.getOption("user").getAsMember(), event.getChannel());
       }

       private void handleRpsChooseCommand(SlashCommandInteractionEvent event) {
           if (!RockPaperScissors.isActive) {
               event.reply("❌ No active Rock-Paper-Scissors game").setEphemeral(true).queue();
               return;
           }
           if (RockPaperScissors.player1 != event.getMember() && RockPaperScissors.player2 != event.getMember()) {
               event.reply("❌ You are not part of the current game").setEphemeral(true).queue();
               return;
           }
           if (event.getChannel() != RockPaperScissors.channel) {
               event.reply("❌ You can only make choices in the channel you were challenged in").setEphemeral(true).queue();
               return;
           }
           String choice = event.getOption("choice").getAsString().toLowerCase();
           if (!choice.equals("rock") && !choice.equals("paper") && !choice.equals("scissors")) {
               event.reply("❌ Invalid choice! Use rock, paper, or scissors").setEphemeral(true).queue();
               return;
           }
           event.reply("✅ You chose: " + choice).setEphemeral(true).queue();
           RockPaperScissors.makeChoice(event.getMember(), choice);
       }

       private void handleRpsCancelCommand(SlashCommandInteractionEvent event) {
           if (!RockPaperScissors.isActive) {
               event.reply("❌ No active Rock-Paper-Scissors game").setEphemeral(true).queue();
               return;
           }
           if (RockPaperScissors.player1 != event.getMember() && RockPaperScissors.player2 != event.getMember()) {
               event.reply("❌ You are not part of the current game").setEphemeral(true).queue();
               return;
           }
           if (event.getChannel() != RockPaperScissors.channel) {
               event.reply("❌ You can only cancel in the channel you were challenged in").setEphemeral(true).queue();
               return;
           }
           event.reply("Cancelling game...").setEphemeral(true).queue();
           RockPaperScissors.cancelGame();
       }
    }
}
