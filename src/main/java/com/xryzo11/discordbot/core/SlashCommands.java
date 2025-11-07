package com.xryzo11.discordbot.core;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.xryzo11.discordbot.DiscordBot;
import com.xryzo11.discordbot.leaderboard.LeaderboardManager;
import com.xryzo11.discordbot.misc.DiceRoll;
import com.xryzo11.discordbot.misc.RockPaperScissors;
import com.xryzo11.discordbot.misc.RoleRestorer;
import com.xryzo11.discordbot.musicBot.LavaPlayerAudioSendHandler;
import com.xryzo11.discordbot.musicBot.MusicBot;
import com.xryzo11.discordbot.musicBot.SpotifyHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.managers.AudioManager;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class SlashCommands {
    public final MusicBot bot = DiscordBot.musicBot;
    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private Runnable updatePresence = () -> DiscordBot.presenceManager.updatePresence();
    private final LeaderboardManager leaderboardManager = DiscordBot.leaderboardManager;

    public static void safeDefer(SlashCommandInteractionEvent event) {
        if (!event.isAcknowledged()) {
            event.deferReply().queue();
        }
    }

    public static void safeDefer(SlashCommandInteractionEvent event, boolean isEphemeral) {
        if (!event.isAcknowledged()) {
            event.deferReply().setEphemeral(true).queue();
        }
    }

    public void handleCommand(SlashCommandInteractionEvent event) {
        switch(event.getName()) {
            case "join":
                handleJoinCommand(event);
                break;
            case "play":
            case "queue":
                handleQueueCommand(event);
                break;
            case "dequeue":
                handleDequeueCommand(event);
                break;
            case "search":
                handleSearchCommand(event);
                break;
            case "pause":
                safeDefer(event);
                bot.pausePlayer();
                event.getHook().editOriginal("⏸️ Playback paused").queue();
                break;
            case "resume":
                safeDefer(event);
                bot.resumePlayer();
                event.getHook().editOriginal("▶️ Playback resumed").queue();
                break;
            case "clear":
                safeDefer(event);
                MusicBot.isCancelled = true;
                bot.clearQueue();
                event.getHook().editOriginal("🧹 Queue cleared").queue();
                break;
            case "stop":
                safeDefer(event);
                MusicBot.isCancelled = true;
                bot.stopPlayer();
                bot.disableLoop();
                bot.clearQueue();
                event.getHook().editOriginal("⏹️ Playback stopped and disconnected").queue();
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
            case "cancel":
                safeDefer(event);
                MusicBot.isCancelled = true;
                event.getHook().editOriginal("✅ Playlist processing cancelled.").queue();
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
            case "roll":
                handleRollCommand(event);
                break;
            case "restore":
                RoleRestorer.restoreRole(event);
                break;
            case "leaderboard":
                handleLeaderboardCommand(event);
                break;
        }

        scheduler.schedule(updatePresence, 3, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void handleJoinCommand(SlashCommandInteractionEvent event) {
        MusicBot.isCancelled = false;
        Member member = event.getMember();

        if (checkVoiceConnection(event)) return;

        safeDefer(event);

        VoiceChannel voiceChannel = member.getVoiceState().getChannel().asVoiceChannel();

        joinChannel(event);

        event.getHook().editOriginal("🔊 Joined voice channel: " + voiceChannel.getName()).queue();
    }

    private boolean checkVoiceConnection(SlashCommandInteractionEvent event) {
        Member member = event.getMember();

        if (member == null || !member.getVoiceState().inAudioChannel()) {
            event.reply("❌ You must be in a voice channel!").setEphemeral(true).queue();
            return true;
        }

        return isInVoiceChannel(event);
    }

    private boolean isInVoiceChannel(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();

        if (guild.getAudioManager().isConnected()) {
            event.reply("❌ Bot is already in a voice channel!").setEphemeral(true).queue();
            return true;
        }

        boolean isConnectedAnywhere = BotHolder.getJDA().getGuilds().stream()
                .anyMatch(g -> g.getAudioManager().isConnected());
        if (isConnectedAnywhere) {
            event.reply("❌ Bot is already in a voice channel in another server!").setEphemeral(true).queue();
            return true;
        }

        return false;
    }

    private void joinChannel(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();

        VoiceChannel voiceChannel = member.getVoiceState().getChannel().asVoiceChannel();

        ensureVoiceConnection(guild, voiceChannel);
        bot.disableLoop();
        bot.clearQueue();
        bot.stopPlayer();
        bot.resumePlayer();
        event.getHook().editOriginal("🔊 Joined voice channel: " + voiceChannel.getName()).queue();
    }

    private void joinAndWait(SlashCommandInteractionEvent event) {
        if (!checkVoiceConnection(event)) {
            joinChannel(event);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleQueueCommand(SlashCommandInteractionEvent event) {
        MusicBot.isCancelled = false;
        String url = event.getOption("url").getAsString();
        Guild guild = event.getGuild();
        Member member = event.getMember();

        joinAndWait(event);

        if (url.contains("spotify")) {
            SpotifyHandler.handleSpotifyUrl(event, url);
            return;
        }

        if (!url.contains("youtube.com") && !url.contains("youtu.be") && !url.contains("youtube.pl")) {
            event.reply("❌ URL must contain a youtube or a spotify link!").setEphemeral(true).queue();
            return;
        }

        if (url.contains("radio") || url.contains("stream") || url.contains("live")) {
            event.reply("❌ Radio or stream URLs are not supported").setEphemeral(true).queue();
            return;
        }

        bot.queue(event, url);
    }

    private void handleDequeueCommand(SlashCommandInteractionEvent event) {
        MusicBot.isCancelled = false;
        int position = event.getOption("position").getAsInt();
        int queueSize = MusicBot.trackQueue.size();
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

        if (position < 1) {
            event.reply("❌ Position must be a positive integer").setEphemeral(true).queue();
            return;
        }

        if (position > queueSize) {
            event.reply("❌ Position exceeds queue size").setEphemeral(true).queue();
            return;
        }

        bot.dequeueTrack(event, position);
    }

    private void handleSearchCommand(SlashCommandInteractionEvent event) {
        MusicBot.isCancelled = false;
        String query = event.getOption("query").getAsString();
        Guild guild = event.getGuild();
        Member member = event.getMember();

        joinAndWait(event);

        if (query.contains("youtube.com") || query.contains("youtu.be") || query.contains("youtube.pl")) {
            event.reply("❌ Please use /play or /queue for direct URLs").setEphemeral(true).queue();
            return;
        }

        bot.search(event, query, guild, member);
    }

    private void handleListCommand(SlashCommandInteractionEvent event) {
        safeDefer(event);
        MusicBot.isCancelled = false;
        String queueList = bot.getQueueList();
        event.getHook().editOriginal(queueList).queue();
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

        safeDefer(event);

        AudioTrack track = bot.currentTrack;
        String title = track.getUserData() != null ? track.getUserData().toString() : track.getInfo().identifier;
        bot.skipCurrentTrack();
        event.getHook().editOriginal("⏭️ Skipped: " + title).queue();
    }

    private void handleLoopCommand(SlashCommandInteractionEvent event) {
        safeDefer(event);
        MusicBot.isCancelled = false;
        bot.toggleLoop();
        String status = bot.isLoopEnabled() ? "enabled" : "disabled";
        event.getHook().editOriginal("🔁 Queue loop " + status).queue();
    }

    private void handleShuffleCommand(SlashCommandInteractionEvent event) {
        MusicBot.isCancelled = false;

        if (MusicBot.trackQueue.size() <= 0) {
            event.reply("❌ Queue is empty.").setEphemeral(true).queue();
            return;
        }

        safeDefer(event);

        bot.shuffleQueue();
        event.getHook().editOriginal("🔀 Queue shuffled").queue();
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

        if (!url.contains("youtube") && !url.contains("youtu.be")) {
            event.reply("❌ Only YouTube URLs are supported for pre-loading").setEphemeral(true).queue();
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

        Member member = event.getMember();
        Guild guild = event.getGuild();

        joinAndWait(event);

        bot.addPreloaded(event);
    }

   private void handleRpsChallengeCommand(SlashCommandInteractionEvent event) {
     if (RockPaperScissors.isActive) {
            event.reply("❌ A Rock-Paper-Scissors game is already in progress").queue();
            return;
     }
     event.reply("🔄 Setting up game...").setEphemeral(true).queue();
     safeDefer(event);
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
       safeDefer(event);
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
       safeDefer(event);
       RockPaperScissors.cancelGame();
   }

   private void handleRollCommand(SlashCommandInteractionEvent event) {
        int sides;
        int count;

        if (event.getOption("sides") == null) {
            sides = 6;
        } else {
            sides = Objects.requireNonNull(event.getOption("sides")).getAsInt();
        }
        if (event.getOption("times") == null) {
            count = 1;
        } else {
            count = Objects.requireNonNull(event.getOption("times")).getAsInt();
        }

        if (sides <= 1) {
            event.reply("❌ Number of sides must be greater than 1").setEphemeral(true).queue();
            return;
        } else if (sides > 1000) {
            event.reply("❌ You can roll a maximum of 1000 sided dice").setEphemeral(true).queue();
            return;
        }
        if (count <= 0) {
            event.reply("❌ Number of times must be a positive integer").setEphemeral(true).queue();
            return;
        } else if (count > 25) {
            event.reply("❌ You can roll the dice a maximum of 25 times at once").setEphemeral(true).queue();
            return;
        }

       safeDefer(event);
       DiceRoll.rollDice(event, sides, count);
   }

   private void handleLeaderboardCommand(SlashCommandInteractionEvent event) {
       safeDefer(event, true);
       String leaderboard;
       if (event.getOption("user" ) != null) {
           try {
               Member member = event.getOption("user").getAsMember();
               leaderboard = leaderboardManager.memberLeaderboardToString(member);
           } catch (Exception e) {
               event.getHook().editOriginal("❌ Could not retrieve user stats. Make sure the user has sent messages in this server.").queue();
               return;
           }
       } else {
           leaderboard = leaderboardManager.topLeaderboardToString();
       }
       event.getHook().editOriginal(leaderboard).queue();
   }
}
