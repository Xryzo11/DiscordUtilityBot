package com.xryzo11.discordbot.utils.listeners;

import com.xryzo11.discordbot.DiscordBot;
import com.xryzo11.discordbot.leaderboard.LeaderboardManager;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class MessageListener extends ListenerAdapter {
    private final LeaderboardManager leaderboardManager = DiscordBot.leaderboardManager;

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }
        if (event.getMember() == null) {
            return;
        }
        leaderboardManager.messageSent(event);
    }
}
