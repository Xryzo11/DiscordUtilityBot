package com.xryzo11.discordbot.core.web;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xryzo11.discordbot.DiscordBot;

import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    private static final Map<String, SessionData> sessions = new ConcurrentHashMap<>();
    private static final String SESSIONS_FILE = "data/sessions.json";
    private static final Gson gson = new Gson();

    public static class SessionData {
        String userId;
        long expiryTime;

        public SessionData(String userId, long expiryTime) {
            this.userId = userId;
            this.expiryTime = expiryTime;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }

        public String getUserId() {
            return userId;
        }
    }

    public static void loadSessions() {
        try {
            java.io.File file = new java.io.File(SESSIONS_FILE);
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    Type type = new TypeToken<ConcurrentHashMap<String, SessionData>>(){}.getType();
                    Map<String, SessionData> loadedSessions = gson.fromJson(reader, type);

                    if (loadedSessions != null) {
                        loadedSessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
                        sessions.putAll(loadedSessions);
                        System.out.println(DiscordBot.getTimestamp() + "[dashboard] Loaded " + sessions.size() + " active session(s) from file");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println(DiscordBot.getTimestamp() + "[dashboard] Error loading sessions: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void saveSessions() {
        try {
            java.io.File file = new java.io.File(SESSIONS_FILE);
            file.getParentFile().mkdirs();

            sessions.entrySet().removeIf(entry -> entry.getValue().isExpired());

            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(sessions, writer);
            }
        } catch (Exception e) {
            System.err.println(DiscordBot.getTimestamp() + "[dashboard] Error saving sessions: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String createSession(String userId) {
        String sessionToken = UUID.randomUUID().toString();
        long expiryTime = System.currentTimeMillis() + (24 * 60 * 60 * 1000);
        sessions.put(sessionToken, new SessionData(userId, expiryTime));
        saveSessions();
        return sessionToken;
    }

    public static void createSessionWithToken(String sessionToken, String userId) {
        long expiryTime = System.currentTimeMillis() + (24 * 60 * 60 * 1000);
        sessions.put(sessionToken, new SessionData(userId, expiryTime));
        saveSessions();
    }

    public static SessionData getSession(String sessionToken) {
        if (sessionToken == null) {
            return null;
        }
        SessionData session = sessions.get(sessionToken);
        if (session != null && session.isExpired()) {
            sessions.remove(sessionToken);
            saveSessions();
            return null;
        }
        return session;
    }

    public static void removeSession(String sessionToken) {
        if (sessionToken != null) {
            sessions.remove(sessionToken);
            saveSessions();
        }
    }

    public static boolean isValidSession(String sessionToken) {
        return getSession(sessionToken) != null;
    }
}

