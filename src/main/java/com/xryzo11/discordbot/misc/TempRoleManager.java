package com.xryzo11.discordbot.misc;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xryzo11.discordbot.DiscordBot;
import com.xryzo11.discordbot.core.BotSettings;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;

import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TempRoleManager {
    private static final String FILE_PATH = DiscordBot.configDirectory + "temp-roles.json";
    private static final Gson gson = new Gson();
    public static List<TempRole> tempRoles = new ArrayList<>();

    public static class TempRole {
        public long roleId;
        public long channelId;
        public boolean enabled;
    }

    public static void loadTempRoles() {
        try (Reader reader = new FileReader(FILE_PATH)) {
            Type listType = new TypeToken<List<TempRoleManager.TempRole>>(){}.getType();
            tempRoles = gson.fromJson(reader, listType);
            if (tempRoles == null) tempRoles = new ArrayList<>();
        } catch (IOException e) {
            tempRoles = new ArrayList<>();
        }
    }

    public static void saveTempRoles() {
        try {
            File file = new File(FILE_PATH);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (Writer writer = new FileWriter(file)) {
                gson.toJson(tempRoles, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean isBound(long channelId) {
        return tempRoles.stream().anyMatch(r -> r.channelId == channelId && r.enabled);
    }

    public static void addTempRole(long roleId, long channelId) {
        TempRoleManager.TempRole r = new TempRoleManager.TempRole();
        r.roleId = roleId;
        r.channelId = channelId;
        r.enabled = true;
        tempRoles.add(r);
        if (BotSettings.isDebug()) {
            System.out.println("[tempRoleManager] Added temp role: " + r.roleId + ", " + r.channelId);
        }
        saveTempRoles();
    }

    public static void removeTempRole(long roleId, long channelId) {
        tempRoles.removeIf(r -> r.roleId == roleId && r.channelId == channelId);
        if (BotSettings.isDebug()) {
            System.out.println("[tempRoleManager] Removed temp role: " + roleId + ", " + channelId);
        }
        saveTempRoles();
    }

    public static List<TempRole> getTempRoles() {
        return new ArrayList<>(tempRoles);
    }

    public static void setRoleEnabled(long roleId, long channelId, boolean enabled) {
        boolean found = false;
        for (TempRoleManager.TempRole r : tempRoles) {
            if (r.roleId == roleId && r.channelId == channelId) {
                r.enabled = enabled;
                found = true;
                if (BotSettings.isDebug()) System.out.println("[tempRoleManager] Updated role: " + r.roleId + ", " + r.channelId + ", enabled=" + r.enabled);
                break;
            }
        }
        if (!found) {
            if (BotSettings.isDebug()) System.out.println("[tempRoleManager] No matching role found to update!");
        }
        saveTempRoles();
    }

    public static void execute(GuildVoiceUpdateEvent event, long userId, String user, String channel, boolean isJoining) {
        Member member = event.getGuild().getMemberById(userId);
        Guild guild = event.getGuild();
        if (member != null && guild != null) {
            if (isJoining) {
                for (TempRoleManager.TempRole r : tempRoles) {
                    if (r.channelId == event.getChannelJoined().getIdLong() && r.enabled) {
                        guild.addRoleToMember(member, guild.getRoleById(r.roleId)).queueAfter(1, TimeUnit.SECONDS);
                        String roleName = guild.getRoleById(r.roleId).getName();
                        if (BotSettings.isDebug()) {
                            System.out.println("[tempRoleManager] Assigned temp role (" + roleName + ") to " + user + " in channel " + channel);
                        }
                    }
                }
            } else {
                for (TempRoleManager.TempRole r : tempRoles) {
                    if (r.channelId == event.getChannelLeft().getIdLong() && r.enabled) {
                        guild.removeRoleFromMember(member, guild.getRoleById(r.roleId)).queue();
                        String roleName = guild.getRoleById(r.roleId).getName();
                        if (BotSettings.isDebug()) {
                            System.out.println("[tempRoleManager] Removed temp role (" + roleName + ") from " + user + " in channel " + channel);
                        }
                    }
                }
            }
        } else {
            System.out.println("[tempRoleManager] Error executing temp role on " + user + " in channel " + channel);
        }
    }
}
