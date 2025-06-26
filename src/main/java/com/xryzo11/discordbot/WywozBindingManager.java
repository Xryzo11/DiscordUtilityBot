package com.xryzo11.discordbot;

import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

public class WywozBindingManager {
    private static final String FILE_PATH = "bindings.json";
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
        saveBindings();
    }

    public static void removeBinding(long userId, long channelId) {
        bindings.removeIf(b -> b.userId == userId && b.channelId == channelId);
        saveBindings();
    }

    public static List<Binding> getBindings() {
        return new ArrayList<>(bindings);
    }

    public static void setBindingEnabled(long userId, long channelId, boolean enabled) {
        System.out.println("setBindingEnabled called: userId=" + userId + ", channelId=" + channelId + ", enabled=" + enabled);
        System.out.println("Current bindings:");
        for (Binding b : bindings) {
            System.out.println("  userId=" + b.userId + ", channelId=" + b.channelId + ", enabled=" + b.enabled);
        }
        boolean found = false;
        for (Binding b : bindings) {
            if (b.userId == userId && b.channelId == channelId) {
                b.enabled = enabled;
                found = true;
                System.out.println("Updated binding: " + b.userId + ", " + b.channelId + ", enabled=" + b.enabled);
                break;
            }
        }
        if (!found) {
            System.out.println("No matching binding found to update!");
        }
        saveBindings();
    }
}