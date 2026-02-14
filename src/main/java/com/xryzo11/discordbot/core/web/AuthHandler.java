package com.xryzo11.discordbot.core.web;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xryzo11.discordbot.DiscordBot;
import com.xryzo11.discordbot.core.BotHolder;
import com.xryzo11.discordbot.core.Config;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import spark.Request;
import spark.Response;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static spark.Spark.*;

public class AuthHandler {
    private static final Gson gson = new Gson();

    public static void registerRoutes() {
        get("/web-auth", AuthHandler::getWebAuth, gson::toJson);
        post("/log-auth", AuthHandler::logAuth, gson::toJson);
        get("/auth/discord/callback", AuthHandler::discordCallback);
        get("/auth/validate", AuthHandler::validateAuth, gson::toJson);
        get("/auth/user-info", AuthHandler::getUserInfo, gson::toJson);
        post("/auth/logout", AuthHandler::logout, gson::toJson);
        post("/auth/password-login", AuthHandler::passwordLogin, gson::toJson);
    }

    public static boolean isAuthenticated(Request req) {
        String authType = Config.getWebAuthType();

        if (authType.equals("none")) {
            return true;
        }

        String sessionToken = req.queryParams("sessionToken");
        if (sessionToken == null) {
            sessionToken = req.cookie("sessionToken");
        }

        return SessionManager.isValidSession(sessionToken);
    }

    private static Object getWebAuth(Request req, Response res) {
        res.type("application/json");

        Map<String, Object> data = new HashMap<>();
        String authType = Config.getWebAuthType();
        data.put("type", authType);

        if (authType.equals("password")) {
            data.put("password", Config.getWebAuthPassword());
        } else if (authType.equals("discord")) {
            data.put("clientId", Config.getDiscordClientId());
            data.put("redirectUri", Config.getDiscordRedirectUri());
            data.put("guildId", Config.getGuildId());
        }

        return data;
    }

    private static Object logAuth(Request req, Response res) {
        res.type("application/json");
        Map<String, Object> result = new HashMap<>();

        try {
            String source = req.queryParams("source");
            String page = req.queryParams("page");
            boolean success = Boolean.parseBoolean(req.queryParams("success"));
            String clientIp = req.ip();

            String pageInfo = (page != null && !page.isEmpty()) ? " on page: " + page : "";

            if (success) {
                System.out.println(DiscordBot.getTimestamp() + "[" + source + "] Authentication successful from " + clientIp + pageInfo);
            } else {
                System.err.println(DiscordBot.getTimestamp() + "[" + source + "] WARNING: Authentication failed from " + clientIp + pageInfo);
            }

            result.put("logged", true);
        } catch (Exception e) {
            result.put("logged", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    private static Object discordCallback(Request req, Response res) {
        String code = req.queryParams("code");
        String error = req.queryParams("error");

        System.out.println(DiscordBot.getTimestamp() + "[discord-oauth] Callback received - code: " + (code != null ? "present" : "null") + ", err: " + error);

        if (error != null) {
            return "<html><body>" +
                   "<h1>❌ Authentication cancelled</h1>" +
                   "<script>" +
                   "console.log('Discord OAuth cancelled by user');" +
                   "if (window.opener) {" +
                   "  window.opener.postMessage({success: false, error: 'user_cancelled'}, '*');" +
                   "  setTimeout(function() { window.close(); }, 1000);" +
                   "}" +
                   "</script>" +
                   "</body></html>";
        }

        if (code == null) {
            res.status(400);
            return "<html><body>" +
                   "<h1>❌ Error: No authorization code provided</h1>" +
                   "<script>" +
                   "console.log('No authorization code in callback');" +
                   "if (window.opener) {" +
                   "  window.opener.postMessage({success: false, error: 'no_code'}, '*');" +
                   "  setTimeout(function() { window.close(); }, 2000);" +
                   "}" +
                   "</script>" +
                   "</body></html>";
        }

        try {
            String tokenResponse = exchangeCodeForToken(code);
            JsonObject tokenJson = JsonParser.parseString(tokenResponse).getAsJsonObject();
            String accessToken = tokenJson.get("access_token").getAsString();

            String userInfoResponse = getUserInfo(accessToken);
            JsonObject userInfo = JsonParser.parseString(userInfoResponse).getAsJsonObject();
            String userId = userInfo.get("id").getAsString();

            if (!hasAdminPermissions(userId)) {
                System.err.println(DiscordBot.getTimestamp() + "[discord-oauth] WARNING: Authentication failed - User " + userId + " does not have admin permissions in guild " + Config.getGuildId());
                return "<html><body>" +
                       "<h1>🔒 Access Denied</h1>" +
                       "<p>You don't have administrator permissions in the configured server</p>" +
                       "<script>" +
                       "if (window.opener) {" +
                       "  window.opener.postMessage({success: false, error: 'no_permissions'}, '*');" +
                       "  setTimeout(function() { window.close(); }, 2000);" +
                       "}" +
                       "</script>" +
                       "</body></html>";
            }

            String sessionToken = SessionManager.createSession(userId);

            System.out.println(DiscordBot.getTimestamp() + "[discord-oauth] Authentication successful for user: " + userId);
            System.out.println(DiscordBot.getTimestamp() + "[discord-oauth] Session token created: " + sessionToken.substring(0, 8) + "...");

            return "<html><head><title>Discord Auth Success</title></head><body>" +
                   "<h1>✅ Authentication successful!</h1>" +
                   "<p>This window will close automatically...</p>" +
                   "<p style='color: gray; font-size: 12px;'>If the window doesn't close, you can close it manually.</p>" +
                   "<script>" +
                   "console.log('Discord OAuth callback page loaded');" +
                   "console.log('Session token:', '" + sessionToken + "');" +
                   "console.log('Window opener exists:', !!window.opener);" +
                   "console.log('Sending success message to parent window');" +
                   "if (window.opener) {" +
                   "  try {" +
                   "    window.opener.postMessage({success: true, sessionToken: '" + sessionToken + "'}, '*');" +
                   "    console.log('Message sent successfully');" +
                   "  } catch (e) {" +
                   "    console.error('Error sending message:', e);" +
                   "  }" +
                   "  console.log('Closing window in 1 second');" +
                   "  setTimeout(function() { " +
                   "    console.log('Attempting to close window'); " +
                   "    window.close(); " +
                   "  }, 1000);" +
                   "} else {" +
                   "  console.error('No window.opener available');" +
                   "  document.body.innerHTML = '<h1>Authentication successful!</h1><p>Please close this window and return to the main page.</p><p>Session token: " + sessionToken + "</p>';" +
                   "}" +
                   "</script>" +
                   "</body></html>";
        } catch (Exception e) {
            System.err.println(DiscordBot.getTimestamp() + "[discord-oauth] Authentication error: " + e.getMessage());
            e.printStackTrace();
            return "<html><body>" +
                   "<h1>❌ Authentication error</h1>" +
                   "<p>" + e.getMessage() + "</p>" +
                   "<script>" +
                   "console.error('Discord OAuth error:', '" + e.getMessage() + "');" +
                   "console.log('Sending error message to parent window');" +
                   "if (window.opener) {" +
                   "  window.opener.postMessage({success: false, error: 'server_error'}, '*');" +
                   "  setTimeout(function() { window.close(); }, 2000);" +
                   "}" +
                   "</script>" +
                   "</body></html>";
        }
    }

    private static Object validateAuth(Request req, Response res) {
        res.type("application/json");
        Map<String, Object> result = new HashMap<>();

        String sessionToken = req.queryParams("sessionToken");
        SessionManager.SessionData session = SessionManager.getSession(sessionToken);

        if (session == null) {
            result.put("valid", false);
            return result;
        }

        result.put("valid", true);
        result.put("userId", session.getUserId());
        return result;
    }

    private static Object getUserInfo(Request req, Response res) {
        res.type("application/json");
        Map<String, Object> result = new HashMap<>();

        String sessionToken = req.queryParams("sessionToken");
        SessionManager.SessionData session = SessionManager.getSession(sessionToken);

        if (session == null) {
            result.put("success", false);
            result.put("error", "Invalid session");
            return result;
        }

        String userId = session.getUserId();
        var jda = com.xryzo11.discordbot.core.BotHolder.getJDA();

        if (jda != null && userId != null && !userId.equals("password-user")) {
            try {
                var user = jda.retrieveUserById(userId).complete();
                if (user != null) {
                    result.put("success", true);
                    result.put("userId", userId);
                    result.put("userName", user.getEffectiveName());
                    result.put("userAvatarUrl", user.getEffectiveAvatarUrl());
                } else {
                    result.put("success", false);
                    result.put("error", "User not found");
                }
            } catch (Exception e) {
                result.put("success", false);
                result.put("error", "Failed to fetch user info");
            }
        } else {
            result.put("success", false);
            result.put("error", "User info not available");
        }

        return result;
    }

    private static Object logout(Request req, Response res) {
        res.type("application/json");
        Map<String, Object> result = new HashMap<>();

        String sessionToken = req.queryParams("sessionToken");
        SessionManager.removeSession(sessionToken);

        result.put("success", true);
        return result;
    }

    private static Object passwordLogin(Request req, Response res) {
        res.type("application/json");
        Map<String, Object> result = new HashMap<>();

        String sessionToken = req.queryParams("sessionToken");
        String hashedPassword = req.queryParams("hashedPassword");

        if (sessionToken == null || hashedPassword == null) {
            result.put("success", false);
            result.put("error", "Missing parameters");
            return result;
        }

        if (hashedPassword.equals(Config.getWebAuthPassword())) {
            SessionManager.createSessionWithToken(sessionToken, "password-user");
            result.put("success", true);
        } else {
            result.put("success", false);
            result.put("error", "Invalid password");
        }

        return result;
    }

    private static String exchangeCodeForToken(String code) throws Exception {
        String clientId = Config.getDiscordClientId();
        String clientSecret = Config.getDiscordClientSecret();
        String redirectUri = Config.getDiscordRedirectUri();

        URL url = new URL("https://discord.com/api/oauth2/token");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setDoOutput(true);

        String params = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                       "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8) +
                       "&grant_type=authorization_code" +
                       "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8) +
                       "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(params.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new Exception("Failed to exchange code for token. Response code: " + responseCode);
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

    private static String getUserInfo(String accessToken) throws Exception {
        URL url = new URL("https://discord.com/api/users/@me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new Exception("Failed to get user info. Response code: " + responseCode);
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

    private static boolean hasAdminPermissions(String userId) {
        try {
            JDA jda = BotHolder.getJDA();
            if (jda == null) {
                return false;
            }

            String guildId = Config.getGuildId();
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                return false;
            }

            Member member = guild.getMemberById(userId);
            if (member == null) {
                return false;
            }

            return member.hasPermission(Permission.ADMINISTRATOR);
        } catch (Exception e) {
            System.err.println(DiscordBot.getTimestamp() + "[discord-oauth] Error checking permissions: " + e.getMessage());
            return false;
        }
    }
}

