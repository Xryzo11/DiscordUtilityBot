package com.xryzo11.discordbot.misc;

import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;
import com.xryzo11.discordbot.DiscordBot;
import com.xryzo11.discordbot.core.BotSettings;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

public class WywozBindingManager {
    private static final String FILE_PATH = DiscordBot.configDirectory + "bindings.json";
    private static final Gson gson = new Gson();
    private static List<Binding> bindings = new ArrayList<>();

    public static class Binding {
        public long userId;
        public long channelId;
        public boolean enabled = true;
    }

    public static void loadBindings() {
        try (Reader reader = new FileReader(FILE_PATH)) {
            Type listType = new TypeToken<List<Binding>>(){}.getType();
            bindings = gson.fromJson(reader, listType);
            if (bindings == null) bindings = new ArrayList<>();
        } catch (IOException e) {
            bindings = new ArrayList<>();
        }
    }

    public static void saveBindings() {
        try {
            File file = new File(FILE_PATH);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (Writer writer = new FileWriter(file)) {
                gson.toJson(bindings, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean isBound(long userId, long channelId) {
        return bindings.stream().anyMatch(b -> b.userId == userId && b.channelId == channelId && b.enabled);
    }

    public static void addBinding(long userId, long channelId) {
        Binding b = new Binding();
        b.userId = userId;
        b.channelId = channelId;
        b.enabled = true;
        bindings.add(b);
        if (BotSettings.isDebug()) {
            System.out.println("[wywozBindingManager] Added binding: userId=" + userId + ", channelId=" + channelId);
        }
        saveBindings();
    }

    public static void removeBinding(long userId, long channelId) {
        bindings.removeIf(b -> b.userId == userId && b.channelId == channelId);
        if (BotSettings.isDebug()) {
            System.out.println("[wywozBindingManager] Removed binding: userId=" + userId + ", channelId=" + channelId);
        }
        saveBindings();
    }

    public static List<Binding> getBindings() {
        return new ArrayList<>(bindings);
    }

    public static void setBindingEnabled(long userId, long channelId, boolean enabled) {
        boolean found = false;
        for (Binding b : bindings) {
            if (b.userId == userId && b.channelId == channelId) {
                b.enabled = enabled;
                found = true;
                if (BotSettings.isDebug()) System.out.println("[wywozBindingManager] Updated binding: " + b.userId + ", " + b.channelId + ", enabled=" + b.enabled);
                break;
            }
        }
        if (!found) {
            if (BotSettings.isDebug()) System.out.println("[wywozBindingManager] No matching binding found to update!");
        }
        saveBindings();
    }

    public static void execute(GuildVoiceUpdateEvent event, long userId, String user, String channel) {
        Member member = event.getGuild().getMemberById(userId);
        Guild guild = event.getGuild();
        if (member != null && guild != null) {
            guild.kickVoiceMember(member).queue();
            System.out.println("[wywozBindingManager] Wywoz smieci (" + user + " | " + channel + ")");
        } else {
            System.out.println("[wywozBindingManager] Blad z wywozem smieci  (" + user + " | " + channel + ")");
        }
    }
}