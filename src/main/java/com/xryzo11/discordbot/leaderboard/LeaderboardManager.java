package com.xryzo11.discordbot.leaderboard;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xryzo11.discordbot.DiscordBot;
import com.xryzo11.discordbot.core.BotSettings;
import com.xryzo11.discordbot.misc.WywozBindingManager;
import com.xryzo11.discordbot.utils.comparators.LeaderboardUserComparator;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LeaderboardManager {
    private List<LeaderboardUser> leaderboardUserList;
    private final String FILE_PATH = DiscordBot.configDirectory + "leaderboard_data.json";
    private final File LEADERBOARD_FILE = new File(FILE_PATH);
    private static final Gson gson = new Gson();
    private final int delay = 10;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public LeaderboardManager() {
        if (!LEADERBOARD_FILE.exists()) {
            try {
                File parent = LEADERBOARD_FILE.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                LEADERBOARD_FILE.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        loadFromFile();
        for (LeaderboardUser user : leaderboardUserList) {
            user.setDelayed(false);
        }
        saveToFile();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> scheduler.shutdown()));
    }

    public void sortLeaderboard() {
        leaderboardUserList.sort(new LeaderboardUserComparator());
    }

    public void messageSent(MessageReceivedEvent event) {
        Member member = event.getMember();
        if (member == null) {
            return;
        }
        String userId = member.getId();
        if (leaderboardUserList.stream().noneMatch(user -> user.getUserId().equals(userId))) {
            if (BotSettings.isDebug()) System.out.println("[LeaderboardManager] Adding new user to leaderboard: " + member.getUser().getAsTag());
            leaderboardUserList.add(new LeaderboardUser(userId, 0));
        }
        for (LeaderboardUser user : leaderboardUserList) {
            if (user.getUserId().equals(userId)) {
                if (!user.isDelayed()) user.incrementXp();
                user.incrementMessagesSent();
                user.setDelayed(true);
                scheduler.schedule(() -> {
                    user.setDelayed(false);
                    saveToFile();
                }, delay, TimeUnit.SECONDS);
                break;
            }
        }
        saveToFile();
    }

    public void saveToFile() {
        try {
            File file = LEADERBOARD_FILE;
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (Writer writer = new FileWriter(file)) {
                gson.toJson(leaderboardUserList, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadFromFile() {
        try (Reader reader = new FileReader(FILE_PATH)) {
            Type listType = new TypeToken<List<LeaderboardUser>>(){}.getType();
            leaderboardUserList = gson.fromJson(reader, listType);
            if (leaderboardUserList == null) leaderboardUserList = new ArrayList<>();
        } catch (IOException e) {
            leaderboardUserList = new ArrayList<>();
        }
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
        sb.append("***Top 10 Leaderboard***\n");
        for (int i = 0; i < topUsers.size(); i++) {
            LeaderboardUser user = topUsers.get(i);
            sb.append(i + 1).append(". <@").append(user.getUserId()).append("> - ")
                    .append("***").append(user.getXp()).append(" XP").append("***")
                    .append(" | ***").append(user.getMessagesSent()).append("*** total messages\n");
        }
        return sb.toString();
    }

    public String memberLeaderboardToString(Member member) {
        StringBuilder sb = new StringBuilder();
        LeaderboardUser[] users = getMemberLeaderboard(member);
        sb.append("***Leaderboard Around <@").append(member.getId()).append(">***\n");
        for (int i = 0; i < users.length; i++) {
            LeaderboardUser user = users[i];
            if (user != null) {
                int rank = leaderboardUserList.indexOf(user) + 1;
                sb.append(rank).append(". <@").append(user.getUserId()).append("> - ")
                        .append("***").append(user.getXp()).append(" XP").append("***")
                        .append(" | ***").append(user.getMessagesSent()).append("*** total messages\n");
            }
        }
        return sb.toString();
    }
}
