package com.xryzo11.discordbot;

import com.sedmelluq.discord.lavaplayer.player.*;
import com.sedmelluq.discord.lavaplayer.player.event.*;
import com.sedmelluq.discord.lavaplayer.source.http.*;
import com.sedmelluq.discord.lavaplayer.tools.*;
import com.sedmelluq.discord.lavaplayer.track.*;
import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.*;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.managers.AudioManager;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;

public class DiscordBot {
    private final AudioPlayerManager playerManager;
    private final AudioPlayer player;
    private final LinkedBlockingQueue<AudioTrack> trackQueue = new LinkedBlockingQueue<>();
    private AudioTrack currentTrack;

    public DiscordBot() {
        playerManager = new DefaultAudioPlayerManager();
        playerManager.registerSourceManager(new HttpAudioSourceManager());
        player = playerManager.createPlayer();
        playerManager.getConfiguration().setFilterHotSwapEnabled(true);

        AudioProcessor.startHttpServer();
        AudioProcessor.startCleanupScheduler();

        player.addListener(new AudioEventAdapter() {
            @Override
            public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
                currentTrack = null;
                if (endReason.mayStartNext) {
                    playNextTrack();
                } else if (trackQueue.isEmpty()) {
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            if (player.getPlayingTrack() == null) {
                                disconnectFromAllVoiceChannels();
                            }
                        }
                    }, 30000);
                }
            }

            @Override
            public void onTrackStart(AudioPlayer player, AudioTrack track) {
                currentTrack = track;
            }
        });
    }

    public static void main(String[] args) throws Exception {
        DiscordBot bot = new DiscordBot();
//        String token = System.getenv("DISCORD_BOT_TOKEN");
        String token = "MTE0Njk1NTA2MjU0ODExOTU2Mw.GLhfzj._hnZysbjS-g-S1Qocph117_WcCgaKG9q5HdEao";
        JDA jda = JDABuilder.createDefault(token)
                .addEventListeners(new VoiceJoinListener())
                .addEventListeners(new SlashCommandInteractionEventListener(bot))
                .build();
        BotHolder.setJDA(jda);
        jda.updateCommands().addCommands(
                Commands.slash("join", "Join voice channel"),
                Commands.slash("queue", "Queue YouTube audio")
                        .addOption(OptionType.STRING, "url", "YouTube URL", true),
                Commands.slash("pause", "Pause current playback"),
                Commands.slash("resume", "Resume playback"),
                Commands.slash("clear", "Clear the queue"),
                Commands.slash("stop", "Stop playback and disconnect"),
                Commands.slash("list", "List current queue"),
                Commands.slash("skip", "Skip the current track")
        ).queue();
        WywozBindingManager.loadBindings();
        Dashboard.start();
    }

    public void playTrack(AudioTrack track) {
        if (player.getPlayingTrack() == null) {
            player.playTrack(track);
        } else {
            trackQueue.offer(track);
        }
    }

    public void playNextTrack() {
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

    private void disconnectFromAllVoiceChannels() {
        JDA jda = BotHolder.getJDA();
        if (jda != null) {
            for (Guild guild : jda.getGuilds()) {
                if (guild.getAudioManager().isConnected()) {
                    guild.getAudioManager().closeAudioConnection();
                }
            }
        }
    }

    public static class VoiceJoinListener extends ListenerAdapter {
        @Override
        public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
            if (event.getChannelJoined() != null) {
                String user = event.getMember().getEffectiveName();
                String channel = event.getChannelJoined().getName();
                long channelId = event.getChannelJoined().getIdLong();
                long userId = event.getMember().getUser().getIdLong();
                if (BotSettings.isDebug()) {
                    System.out.println(user + " joined voice channel: " + channel);
                }
                if (BotSettings.isWywozSmieci()) {
                    if (WywozBindingManager.isBound(userId, channelId)) {
                        Member member = event.getGuild().getMemberById(userId);
                        Guild guild = event.getGuild();
                        if (member != null && guild != null) {
                            guild.kickVoiceMember(member).queue();
                            System.out.println("Wywoz smieci (" + user + " | " + channel + ")");
                        } else {
                            System.out.println("Blad z wywozem smieci  (" + user + " | " + channel + ")");
                        }
                    }
                }
            }
        }
    }

    public static class SlashCommandInteractionEventListener extends ListenerAdapter {
        private final DiscordBot bot;
        private final ExecutorService commandExecutor = Executors.newCachedThreadPool();

        public SlashCommandInteractionEventListener(DiscordBot bot) {
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
                        hook.editOriginal("⏳ Processing YouTube URL...").queue();

                        AudioTrackInfo trackInfo = AudioProcessor.processYouTubeAudio(url).get();

                        Thread.sleep(1000);

                        File audioFile = new File(AudioProcessor.AUDIO_DIR + trackInfo.identifier + ".mp3");
                        if (!audioFile.exists() || !audioFile.canRead()) {
                            hook.editOriginal("❌ Audio file not ready. Please try again.").queue();
                            return;
                        }

                        bot.playerManager.loadItem(trackInfo.uri, new AudioLoadResultHandler() {
                            @Override
                            public void trackLoaded(AudioTrack track) {
                                track.setUserData(trackInfo.title);
                                bot.trackQueue.offer(track);
                                hook.editOriginal("✅ Added to queue: " + trackInfo.title).queue();

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

    public String getQueueList() {
        StringBuilder builder = new StringBuilder();

        if (currentTrack != null) {
            builder.append("**Now Playing:**\n")
                    .append(formatTrackInfo(currentTrack))
                    .append("\n\n");
        }

        if (trackQueue.isEmpty()) {
            builder.append("Queue is empty");
        } else {
            builder.append("**Queue:**\n");
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
        long duration = track.getDuration();

        String uri = info.uri;
        String youtubeId = uri.substring(uri.lastIndexOf('/') + 1).replace(".mp3", "");

        String durationStr = String.format("%d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(duration),
                TimeUnit.MILLISECONDS.toSeconds(duration) % 60
        );

        String youtubeUrl = "https://www.youtube.com/watch?v=" + youtubeId;

        String title = track.getUserData() != null ? track.getUserData().toString() : youtubeId;

        return String.format("[%s](<%s>) `[%s]`",
                title,
                youtubeUrl,
                durationStr);
    }
}