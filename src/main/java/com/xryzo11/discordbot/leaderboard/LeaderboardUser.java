package com.xryzo11.discordbot.leaderboard;

import java.util.concurrent.atomic.AtomicBoolean;

public class LeaderboardUser {
    private final String userId;
    private int messagesSent;
    private int xp;
    private AtomicBoolean isDelayed = new AtomicBoolean(false);

    public LeaderboardUser(String userId, int messagesSent) {
        this.userId = userId;
        this.messagesSent = messagesSent;
        if (this.isDelayed == null) this.isDelayed = new AtomicBoolean(false);
        this.isDelayed.set(false);
    }

    public String getUserId() {
        return userId;
    }

    public int getMessagesSent() {
        return messagesSent;
    }

    public int getXp() {
        return xp;
    }

    public boolean isDelayed() {
        return isDelayed.get();
    }

    public void setDelayed(boolean delayed) {
        if (isDelayed == null) isDelayed = new AtomicBoolean(delayed);
        else isDelayed.set(delayed);
    }

    public void incrementMessagesSent() {
        this.messagesSent++;
    }

    public void incrementXp(int value) {
        this.xp += value;
    }
}
