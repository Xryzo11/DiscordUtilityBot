package com.xryzo11.discordbot.leaderboard;

import com.xryzo11.discordbot.utils.comparators.LeaderboardUserComparator;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;

public class LeaderboardManager {
    public List<LeaderboardUser> leaderboardUserList = new ArrayList<>();

    public LeaderboardManager() {}

    public void sortLeaderboard() {
        leaderboardUserList.sort(new LeaderboardUserComparator());
    }

    public void messageSent(MessageReceivedEvent event) {
        Member member = event.getMember();
        if (member == null) {
            return;
        }
        String userId = member.getId();
        for (LeaderboardUser user : leaderboardUserList) {
            if (user.getUserId().equals(userId)) {
                user.incrementMessagesSent();
                return;
            }
        }
    }

    public void saveToFile() {

    }

    public void loadFromFile() {

    }

    public List<LeaderboardUser> getTopUsers() {
        sortLeaderboard();
        return leaderboardUserList.subList(0, Math.min(10, leaderboardUserList.size()));
    }

    private LeaderboardUser[] getMemberLeaderboard(Member member) {
        LeaderboardUser[] result = new LeaderboardUser[5];
        String userId = member.getId();
        for (LeaderboardUser user : leaderboardUserList) {
            if (user.getUserId().equals(userId)) {
                result[2] = user;
                if (leaderboardUserList.indexOf(user) > 0) {
                    result[1] = leaderboardUserList.get(leaderboardUserList.indexOf(user) - 1);
                    if (leaderboardUserList.indexOf(user) > 1) {
                        result[0] = leaderboardUserList.get(leaderboardUserList.indexOf(user) - 2);
                    }
                }
                if (leaderboardUserList.indexOf(user) < leaderboardUserList.size() - 1) {
                    result[3] = leaderboardUserList.get(leaderboardUserList.indexOf(user) + 1);
                    if (leaderboardUserList.indexOf(user) < leaderboardUserList.size() - 2) {
                        result[4] = leaderboardUserList.get(leaderboardUserList.indexOf(user) + 2);
                    }
                }
            }
        }
        return result;
    }

    public String topLeaderboardToString() {
        StringBuilder sb = new StringBuilder();
        List<LeaderboardUser> topUsers = getTopUsers();
        for (int i = 0; i < topUsers.size(); i++) {
            LeaderboardUser user = topUsers.get(i);
            sb.append(i + 1).append(". <@").append(user.getUserId()).append("> - ").append(user.getMessagesSent()).append(" messages\n");
        }
        return sb.toString();
    }

    public String memberLeaderboardToString(Member member) {
        StringBuilder sb = new StringBuilder();
        LeaderboardUser[] users = getMemberLeaderboard(member);
        for (int i = 0; i < users.length; i++) {
            LeaderboardUser user = users[i];
            if (user != null) {
                int rank = leaderboardUserList.indexOf(user) + 1;
                sb.append(rank).append(". <@").append(user.getUserId()).append("> - ").append(user.getMessagesSent()).append(" messages\n");
            }
        }
        return sb.toString();
    }
}
