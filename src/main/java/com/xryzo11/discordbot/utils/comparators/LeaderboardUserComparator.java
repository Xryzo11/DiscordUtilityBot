package com.xryzo11.discordbot.utils.comparators;

import com.xryzo11.discordbot.leaderboard.LeaderboardUser;

import java.util.Comparator;

public class LeaderboardUserComparator implements Comparator<LeaderboardUser> {
    @Override
    public int compare(LeaderboardUser user1, LeaderboardUser user2) {
        return Integer.compare(user2.getMessagesSent(), user1.getMessagesSent());
    }
}
