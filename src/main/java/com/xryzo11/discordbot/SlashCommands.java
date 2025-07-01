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

            event.deferReply().queue(hook -> {
                CompletableFuture.runAsync(() -> {
                    try {
                        hook.editOriginal("⏳ Starting YouTube processing...").queue();

                        AudioTrackInfo trackInfo = AudioProcessor.processYouTubeAudio(url).get();
                        hook.editOriginal("📥 Download complete, preparing audio...").queue();

                        File audioFile = new File(AudioProcessor.AUDIO_DIR + trackInfo.identifier + ".mp3");
                        if (!audioFile.exists() || !audioFile.canRead()) {
                            hook.editOriginal("❌ Audio file not ready. Please try again.").queue();
                            return;
                        }

                        hook.editOriginal("🔄 Loading track into player...").queue();
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
                            public void playlistLoaded(AudioPlaylist playlist) {
                                AudioTrack firstTrack = playlist.getTracks().get(0);
                                bot.trackQueue.offer(firstTrack);
                                hook.editOriginal("✅ Added to queue: " + firstTrack.getInfo().title).queue();
                            }

                            @Override
                            public void noMatches() {
                                hook.editOriginal("❌ No matching audio found").queue();
                            }

                            @Override
                            public void loadFailed(FriendlyException exception) {
                                hook.editOriginal("❌ Failed to load track: " + exception.getMessage()).queue();
                            }
                        });
                    } catch (Exception e) {
                        hook.editOriginal("❌ Error processing track: " + e.getMessage()).queue();
                        e.printStackTrace();
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
    }
}
