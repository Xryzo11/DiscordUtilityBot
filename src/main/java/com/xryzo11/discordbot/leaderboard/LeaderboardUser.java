package com.xryzo11.discordbot.leaderboard;

public class LeaderboardUser {
    private final String userId;
    private int messagesSent;

    public LeaderboardUser(String userId, int messagesSent) {
        this.userId = userId;
        this.messagesSent = messagesSent;
    }

    public String getUserId() {
        return userId;
    }

    public int getMessagesSent() {
        return messagesSent;
    }

    public void incrementMessagesSent() {
        this.messagesSent++;
    }
}
