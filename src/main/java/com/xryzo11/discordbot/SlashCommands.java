package com.xryzo11.discordbot;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;

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
                    event.reply("Not implemented yet :upside_down:").queue();
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
            bot.disableLoop();
            bot.clearQueue();
            bot.stopPlayer();
            bot.resumePlayer();
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

            bot.queue(event, url, guild, member);
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
