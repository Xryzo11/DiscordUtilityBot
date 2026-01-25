package com.xryzo11.discordbot.core;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.xryzo11.discordbot.DiscordBot;
import com.xryzo11.discordbot.leaderboard.LeaderboardManager;
import com.xryzo11.discordbot.misc.DiceRoll;
import com.xryzo11.discordbot.misc.RockPaperScissors;
import com.xryzo11.discordbot.misc.RoleRestorer;
import com.xryzo11.discordbot.musicBot.LavaPlayerAudioSendHandler;
import com.xryzo11.discordbot.musicBot.MusicBot;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.managers.AudioManager;

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
            event.deferReply(false).queue();
        }
    }

    public static void safeDefer(SlashCommandInteractionEvent event, boolean isEphemeral) {
        if (!event.isAcknowledged()) {
            event.deferReply(true).queue();
        }
    }

    public void handleCommand(SlashCommandInteractionEvent event) {
        switch(event.getName()) {
            case "join":
                handleJoinCommand(event);
                break;
            case "play":
            case "queue":
                handleQueueCommand(event, false);
                break;
            case "dequeue":
                handleDequeueCommand(event);
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
                bot.clearQueue();
                event.getHook().editOriginal("🧹 Queue cleared").queue();
                break;
            case "leave":
                safeDefer(event);
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
            case "playnext":
                handleQueueCommand(event, true);
                break;
            case "reorder":
                handleReorderCommand(event);
                break;
            case "save":
                handleSaveCommand(event);
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

        if (event.getMember() != null) {
            leaderboardManager.commandExecuted(event.getMember());
        }
    }

    private void handleJoinCommand(SlashCommandInteractionEvent event) {
        Member member = event.getMember();

        if (checkVoiceConnection(event)) return;

        safeDefer(event);

        VoiceChannel voiceChannel = member.getVoiceState().getChannel().asVoiceChannel();

        joinChannel(event);

        event.getHook().editOriginal("🔊 Joined voice channel: " + voiceChannel.getName()).queue();
    }

    private boolean checkVoiceConnection(SlashCommandInteractionEvent event) {
        return !isUserInVoice(event);
    }

    private boolean isInVoiceChannel(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();

        if (guild.getAudioManager().isConnected()) {
            return true;
        }

        return BotHolder.getJDA().getGuilds().stream()
                .anyMatch(g -> g.getAudioManager().isConnected());
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
    }

    private boolean isUserInVoice(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null || !member.getVoiceState().inAudioChannel()) {
            event.reply("❌ You must be in a voice channel!").setEphemeral(true).queue();
            return false;
        }

        Guild guild = event.getGuild();
        boolean isConnected = guild.getAudioManager().isConnected();
        boolean isConnectedAnywhere = BotHolder.getJDA().getGuilds().stream()
                .anyMatch(g -> g.getAudioManager().isConnected());

        if (isConnected) {
            if (!member.getVoiceState().getChannel().asVoiceChannel().equals(guild.getAudioManager().getConnectedChannel())) {
                event.reply("❌ You must be in the same voice channel as the bot!").setEphemeral(true).queue();
                return false;
            }
        } else if (isConnectedAnywhere) {
            event.reply("❌ Bot is already in a voice channel in another server!").setEphemeral(true).queue();
            return false;
        }

        return true;
    }

    private boolean joinIfNeeded(SlashCommandInteractionEvent event) {
        boolean isConnected = isInVoiceChannel(event);
        if (!isConnected) {
            joinChannel(event);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }

    private void handleQueueCommand(SlashCommandInteractionEvent event, boolean isPlayNext) {
        String track = event.getOption("track").getAsString();

        if (!isUserInVoice(event)) {
            return;
        }

        if (!track.contains("youtube.com") && !track.contains("youtu.be") && !track.contains("youtube.pl") && !track.contains("spotify.com")) {
            if (track.contains("http://") || track.contains("https://") || track.contains("www.")) {
                event.reply("❌ Only YouTube and Spotify links are supported").setEphemeral(true).queue();
                if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[handleQueue] Unsupported link detected, rejecting: " + track);
                return;
            }

            if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[handleQueue] No link detected, searching: " + track);

            if (Config.getGoogleOAuth2Token() == null || Config.getGoogleOAuth2Token().isEmpty() || Config.getGoogleOAuth2Token().equals("YOUR_OAUTH2_TOKEN_HERE")) {
                event.reply("❌ YouTube OAuth2 token is not configured. Please set it in the config file to play YouTube links.").setEphemeral(true).queue();
                if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[handleQueue] YouTube OAuth2 token not configured.");
                return;
            }

            joinIfNeeded(event);
            bot.search(event, track);
            return;
        }
        if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[handleQueue] Link detected: " + track);

        if ((track.contains("playlist") || track.contains("list=")) && isPlayNext) {
            event.reply("❌ Playlists cannot be played next, please use the regular queue command").setEphemeral(true).queue();
            return;
        }

        if (track.contains(" ")) {
            event.reply("❌ Track URL cannot contain spaces").setEphemeral(true).queue();
            if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[handleQueue] Track URL contains spaces, rejecting: " + track);
            return;
        }

        if (track.contains("youtube.com") || track.contains("youtu.be") || track.contains("youtube.pl")) {
            if (Config.getGoogleOAuth2Token() == null || Config.getGoogleOAuth2Token().isEmpty() || Config.getGoogleOAuth2Token().equals("YOUR_OAUTH2_TOKEN_HERE")) {
                event.reply("❌ YouTube OAuth2 token is not configured. Please set it in the config file to play YouTube links.").setEphemeral(true).queue();
                if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[handleQueue] YouTube OAuth2 token not configured.");
                return;
            }
            if (track.contains("/shorts/")) {
                if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[handleQueue] Detected YouTube Shorts URL, converting: " + track);
                track = track.replace("/shorts/", "/watch?v=");
                if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[handleQueue] Converted Shorts URL to standard URL: " + track);
            }
            if (track.contains("radio") || track.contains("stream") || track.contains("live")) {
                event.reply("❌ Radio or stream URLs are not supported").setEphemeral(true).queue();
                if (BotSettings.isDebug()) System.out.println(DiscordBot.getTimestamp() + "[handleQueue] Radio/stream URL detected, rejecting: " + track);
                return;
            }
        }

        joinIfNeeded(event);
        bot.queue(event, track, isPlayNext);
    }

    private void handleDequeueCommand(SlashCommandInteractionEvent event) {
        int position = event.getOption("position").getAsInt();
        int queueSize = MusicBot.trackQueue.size();

        if (!isUserInVoice(event)) {
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

    private void handleListCommand(SlashCommandInteractionEvent event) {
        safeDefer(event);
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
        if (!isUserInVoice(event)) {
            return;
        }

        if (bot.player.getPlayingTrack() == null) {
            event.reply("❌ No track is currently playing").setEphemeral(true).queue();
            return;
        }

        safeDefer(event);

        AudioTrack track = bot.currentTrack;
        String title = track.getInfo().title;
        bot.skipCurrentTrack();
        AudioTrack newTrack = bot.currentTrack;
        if (newTrack == null) {
            event.getHook().editOriginal("⏭️ Skipped: " + title + "\nNo more tracks in the queue.").queue();
            return;
        }
        String newTitle = newTrack.getInfo().title;
        event.getHook().editOriginal("⏭️ Skipped: " + title + "\nNow playing: " + newTitle).queue();
    }

    private void handleLoopCommand(SlashCommandInteractionEvent event) {
        if (!isUserInVoice(event)) {
            return;
        }
        safeDefer(event);
        bot.toggleLoop();
        String status = bot.isLoopEnabled() ? "enabled" : "disabled";
        event.getHook().editOriginal("🔁 Queue loop " + status).queue();
    }

    private void handleShuffleCommand(SlashCommandInteractionEvent event) {
        if (!isUserInVoice(event)) {
            return;
        }

        if (MusicBot.trackQueue.size() <= 0) {
            event.reply("❌ Queue is empty.").setEphemeral(true).queue();
            return;
        }

        safeDefer(event);

        bot.shuffleQueue();
        event.getHook().editOriginal("🔀 Queue shuffled").queue();
    }

    private void handlePlayheadCommand(SlashCommandInteractionEvent event) {
        int hour = event.getOption("hour").getAsInt();
        int minute = event.getOption("minute").getAsInt();
        int second = event.getOption("second").getAsInt();
        int totalSeconds = hour * 3600 + minute * 60 + second;

        if (!isUserInVoice(event)) {
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

    private void handleSaveCommand(SlashCommandInteractionEvent event) {
        if (!Config.isAudioSavingEnabled()) {
            event.reply("❌ Saving tracks is not enabled").setEphemeral(true).queue();
            return;
        }

        String url = event.getOption("url").getAsString();
        String name = event.getOption("name").getAsString();

//        if (url.contains("playlist") || url.contains("list=")) {
//            event.reply("❌ Playlists are not supported, please provide a single track URL").setEphemeral(true).queue();
//            return;
//        }

        if (!url.contains("youtube") && !url.contains("youtu.be") && !url.contains("youtube.pl")) {
            event.reply("❌ Only YouTube URLs are supported for saving.").setEphemeral(true).queue();
            return;
        }

        if (url.contains("radio") || url.contains("stream") || url.contains("live")) {
            event.reply("❌ Radio or stream URLs are not supported").setEphemeral(true).queue();
            return;
        }

        if (Config.getGoogleOAuth2Token() == null || Config.getGoogleOAuth2Token().isEmpty() || Config.getGoogleOAuth2Token().equals("YOUR_OAUTH2_TOKEN_HERE")) {
            event.reply("❌ YouTube OAuth2 token is not configured. Please set it in the config file to play YouTube links.").setEphemeral(true).queue();
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

        bot.save(event);
    }

    private void handleAddCommand(SlashCommandInteractionEvent event) {
        if (!Config.isAudioSavingEnabled()) {
            event.reply("❌ Saving tracks is not enabled").setEphemeral(true).queue();
            return;
        }

        safeDefer(event);

        if (!isUserInVoice(event)) {
            event.getHook().editOriginal("❌ You must be in a voice channel to add a saved track").queue();
            return;
        }
        if (joinIfNeeded(event)) {
            event.getHook().editOriginal("🔊 Joined voice channel: " + event.getMember().getVoiceState().getChannel().asVoiceChannel().getName()).queue();
        }

        bot.addSaved(event);
    }

    private void handleReorderCommand(SlashCommandInteractionEvent event) {
        int fromPosition = event.getOption("from").getAsInt();
        int toPosition = event.getOption("to").getAsInt();
        int queueSize = MusicBot.trackQueue.size();

        if (!isUserInVoice(event)) {
            return;
        }

        if (fromPosition < 1 || toPosition < 1) {
            event.reply("❌ Positions must be positive integers").setEphemeral(true).queue();
            return;
        }

        if (fromPosition > queueSize || toPosition > queueSize) {
            event.reply("❌ Positions exceed queue size").setEphemeral(true).queue();
            return;
        }

        bot.reorderTrack(event, fromPosition, toPosition);
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
