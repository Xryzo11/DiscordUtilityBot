package com.xryzo11.discordbot;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;

import java.io.File;
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
                case "search":
                    handleSearchCommand(event);
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
                    MusicBot.isCancelled = true;
                    bot.clearQueue();
                    event.reply("🧹 Queue cleared").queue();
                    break;
                case "stop":
                    MusicBot.isCancelled = true;
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
                case "preload":
                    handlePreloadCommand(event);
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
            MusicBot.isCancelled = false;
            Guild guild = event.getGuild();
            Member member = event.getMember();

            if (member == null || !member.getVoiceState().inAudioChannel()) {
                event.reply("❌ You must be in a voice channel!").setEphemeral(true).queue();
                return;
            }

            if (guild.getAudioManager().isConnected()) {
                event.reply("❌ Bot is already in a voice channel!").setEphemeral(true).queue();
                return;
            }

            boolean isConnectedAnywhere = BotHolder.getJDA().getGuilds().stream()
                    .anyMatch(g -> g.getAudioManager().isConnected());
            if (isConnectedAnywhere) {
                event.reply("❌ Bot is already in a voice channel in another server!").setEphemeral(true).queue();
                return;
            }
            
            VoiceChannel voiceChannel = member.getVoiceState().getChannel().asVoiceChannel();

            ensureVoiceConnection(guild, voiceChannel);
            bot.disableLoop();
            bot.clearQueue();
            bot.stopPlayer();
            bot.resumePlayer();
            event.reply("🔊 Joined voice channel: " + voiceChannel.getName()).queue();
        }

        private void handleQueueCommand(SlashCommandInteractionEvent event) {
            MusicBot.isCancelled = false;
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

            bot.queue(event, url, guild, member);
        }

        private void handleSearchCommand(SlashCommandInteractionEvent event) {
            MusicBot.isCancelled = false;
            String query = event.getOption("query").getAsString();
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

            bot.search(event, query, guild, member);
        }

        private void handleListCommand(SlashCommandInteractionEvent event) {
            MusicBot.isCancelled = false;
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
            MusicBot.isCancelled = false;
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
            MusicBot.isCancelled = false;
            bot.toggleLoop();
            String status = bot.isLoopEnabled() ? "enabled" : "disabled";
            event.reply("🔁 Queue loop " + status).queue();
        }

        private void handleShuffleCommand(SlashCommandInteractionEvent event) {
            MusicBot.isCancelled = false;
            bot.shuffleQueue();
            event.reply("🔀 Queue shuffled").queue();
        }

        private void handlePlayheadCommand(SlashCommandInteractionEvent event) {
            MusicBot.isCancelled = false;
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

        private void handlePreloadCommand(SlashCommandInteractionEvent event) {
            MusicBot.isCancelled = false;

            if (!Config.isPreloadedEnabled()) {
                event.reply("❌ Pre-loaded tracks are not enabled").setEphemeral(true).queue();
                return;
            }

            String url = event.getOption("url").getAsString();
            String name = event.getOption("name").getAsString();

            if (url.contains("radio") || url.contains("stream") || url.contains("live")) {
                event.reply("❌ Radio or stream URLs are not supported").setEphemeral(true).queue();
                return;
            }

            if (url.contains("playlist") || url.contains("list=")) {
                event.reply("❌ Playlists are not supported, please provide a single track URL").setEphemeral(true).queue();
                return;
            }

            if (name.length() > 20) {
                event.reply("❌ Name must be 20 characters or fewer").setEphemeral(true).queue();
                return;
            }

            if (name.contains(" ")) {
                event.reply("❌ Name cannot contain spaces").setEphemeral(true).queue();
                return;
            }

            for (File file : new File(Config.getPreloadedDirectory()).listFiles()) {
                String fileName = file.getName();
                int dotIndex = fileName.lastIndexOf('.');
                String baseName = (dotIndex > 0) ? fileName.substring(0, dotIndex) : fileName;
                int idIndex = baseName.indexOf(" [(");
                String shortName = (idIndex > 0) ? baseName.substring(0, idIndex) : baseName;
                if (shortName.equalsIgnoreCase(name)) {
                    event.reply("❌ A pre-loaded track with that name already exists").setEphemeral(true).queue();
                    return;
                }
            }

            bot.preload(event);
        }

        private void handleAddCommand(SlashCommandInteractionEvent event) {
            MusicBot.isCancelled = false;

            if (!Config.isPreloadedEnabled()) {
                event.reply("❌ Pre-loaded tracks are not enabled").setEphemeral(true).queue();
                return;
            }

            bot.addPreloaded(event);
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
