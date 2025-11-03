package com.xryzo11.discordbot.utils.comparators;

import com.xryzo11.discordbot.leaderboard.LeaderboardUser;

import java.util.Comparator;

public class LeaderboardUserComparator implements Comparator<LeaderboardUser> {
    @Override
    public int compare(LeaderboardUser user1, LeaderboardUser user2) {
        int comparison = Integer.compare(user2.getXp(), user1.getXp());
        if (comparison == 0) {
            comparison = Integer.compare(user2.getMessagesSent(), user1.getMessagesSent());
        }
        if (comparison == 0) {
            comparison = user1.getUserId().compareTo(user2.getUserId());
        }
        return comparison;
    }
}
