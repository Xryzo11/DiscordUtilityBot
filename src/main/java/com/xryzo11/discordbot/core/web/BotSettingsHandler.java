package com.xryzo11.discordbot.core.web;

import com.google.gson.Gson;
import com.xryzo11.discordbot.DiscordBot;
import com.xryzo11.discordbot.core.BotHolder;
import com.xryzo11.discordbot.misc.TempRoleManager;
import com.xryzo11.discordbot.misc.WywozBindingManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Role;
import spark.Request;
import spark.Response;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static spark.Spark.*;

public class BotSettingsHandler {
    private static final Gson gson = new Gson();

    public static class BindingDetailed {
        public String userName;
        public String channelName;
        public String userId;
        public String channelId;
        public boolean enabled;
    }

    public static class RoleDetailed {
        public String roleName;
        public String roleId;
        public String channelName;
        public String channelId;
        public boolean enabled;
    }

    public static void registerRoutes() {
        get("/get-info", BotSettingsHandler::getInfo, gson::toJson);

        get("/wywoz-initial-status", BotSettingsHandler::getWywozInitialStatus);
        get("/wywoz-status", BotSettingsHandler::getWywozStatus);
        post("/toggle-wywoz", BotSettingsHandler::toggleWywoz);

        get("/temp-role-initial-status", BotSettingsHandler::getTempRoleInitialStatus);
        get("/temp-role-status", BotSettingsHandler::getTempRoleStatus);
        post("/toggle-temp-role-status", BotSettingsHandler::toggleTempRoleStatus);
        post("/add-temp-role", BotSettingsHandler::addTempRole);
        post("/remove-temp-role", BotSettingsHandler::removeTempRole);
        post("/toggle-temp-role", BotSettingsHandler::toggleTempRole);
        get("/temp-roles-detailed", BotSettingsHandler::getTempRolesDetailed);

        get("/debug-status", BotSettingsHandler::getDebugStatus);
        post("/toggle-debug", BotSettingsHandler::toggleDebug);

        post("/add-binding", BotSettingsHandler::addBinding);
        post("/remove-binding", BotSettingsHandler::removeBinding);
        get("/bindings-detailed", BotSettingsHandler::getBindingsDetailed);
        post("/toggle-binding", BotSettingsHandler::toggleBinding);

        post("/force-restart", BotSettingsHandler::forceRestart);
    }

    private static Object getInfo(Request req, Response res) {
        res.type("application/json");
        Map<String, Object> data = new HashMap<>();
        data.put("fullVersion", DiscordBot.fullVersion);
        data.put("lastRestart", DiscordBot.lastRestart);
        return data;
    }

    private static Object getWywozInitialStatus(Request req, Response res) {
        return BotSettings.isWywozSmieciInitial() ? "enabled" : "disabled";
    }

    private static Object getWywozStatus(Request req, Response res) {
        return BotSettings.isWywozSmieciAuto() ? "enabled" : "disabled";
    }

    private static Object toggleWywoz(Request req, Response res) {
        boolean state = Boolean.parseBoolean(req.queryParams("status"));
        BotSettings.setWywozSmieci(state);
        res.redirect("/");
        System.out.println("[dashboard] Wywoz smieci toggled to: " + (state ? "ON" : "OFF"));
        return "Wywoz smieci toggled to: " + (state ? "ON" : "OFF");
    }

    private static Object getTempRoleInitialStatus(Request req, Response res) {
        return BotSettings.isTempRoleInitial() ? "enabled" : "disabled";
    }

    private static Object getTempRoleStatus(Request req, Response res) {
        return BotSettings.isTempRoleAuto() ? "enabled" : "disabled";
    }

    private static Object toggleTempRoleStatus(Request req, Response res) {
        boolean state = Boolean.parseBoolean(req.queryParams("status"));
        BotSettings.setTempRole(state);
        res.redirect("/");
        System.out.println("[dashboard] Temporary role toggled to: " + (state ? "ON" : "OFF"));
        return "Temporary role toggled to: " + (state ? "ON" : "OFF");
    }

    private static Object addTempRole(Request req, Response res) {
        String roleIdStr = req.queryParams("roleId");
        String channelIdStr = req.queryParams("channelId");
        long roleId = Long.parseUnsignedLong(roleIdStr);
        long channelId = Long.parseUnsignedLong(channelIdStr);
        TempRoleManager.addTempRole(roleId, channelId);
        return "OK";
    }

    private static Object removeTempRole(Request req, Response res) {
        long roleId = Long.parseUnsignedLong(req.queryParams("roleId"));
        long channelId = Long.parseUnsignedLong(req.queryParams("channelId"));
        TempRoleManager.removeTempRole(roleId, channelId);
        return "OK";
    }

    private static Object toggleTempRole(Request req, Response res) {
        String roleIdStr = req.queryParams("roleId");
        String channelIdStr = req.queryParams("channelId");
        boolean enabled = Boolean.parseBoolean(req.queryParams("enabled"));
        long roleId = Long.parseUnsignedLong(roleIdStr);
        long channelId = Long.parseUnsignedLong(channelIdStr);
        TempRoleManager.setRoleEnabled(roleId, channelId, enabled);
        return "OK";
    }

    private static Object getTempRolesDetailed(Request req, Response res) {
        res.type("application/json");
        JDA jda = BotHolder.getJDA();
        var detailed = TempRoleManager.getTempRoles().stream().map(r -> {
            RoleDetailed d = new RoleDetailed();
            d.roleId = String.valueOf(r.roleId);
            d.channelId = String.valueOf(r.channelId);
            Role role = jda.getRoleById(d.roleId);
            d.roleName = (jda != null && role != null)
                    ? role.getName()
                    : "(Unknown)";
            d.channelName = (jda != null && jda.getVoiceChannelById(r.channelId) != null)
                    ? jda.getVoiceChannelById(r.channelId).getName()
                    : "(Unknown)";
            d.enabled = r.enabled;
            return d;
        }).collect(Collectors.toList());
        return gson.toJson(detailed);
    }

    private static Object getDebugStatus(Request req, Response res) {
        return BotSettings.isDebug() ? "enabled" : "disabled";
    }

    private static Object toggleDebug(Request req, Response res) {
        boolean state = Boolean.parseBoolean(req.queryParams("status"));
        BotSettings.setDebug(state);
        res.redirect("/");
        System.out.println("[dashboard] Debug mode toggled to: " + (state ? "ON" : "OFF"));
        return "Debug mode toggled to: " + (state ? "ON" : "OFF");
    }

    private static Object addBinding(Request req, Response res) {
        String userIdStr = req.queryParams("userId");
        String channelIdStr = req.queryParams("channelId");
        long userId = Long.parseUnsignedLong(userIdStr);
        long channelId = Long.parseUnsignedLong(channelIdStr);
        WywozBindingManager.addBinding(userId, channelId);
        return "OK";
    }

    private static Object removeBinding(Request req, Response res) {
        long userId = Long.parseUnsignedLong(req.queryParams("userId"));
        long channelId = Long.parseUnsignedLong(req.queryParams("channelId"));
        WywozBindingManager.removeBinding(userId, channelId);
        return "OK";
    }

    private static Object getBindingsDetailed(Request req, Response res) {
        res.type("application/json");
        JDA jda = BotHolder.getJDA();
        var detailed = WywozBindingManager.getBindings().stream().map(b -> {
            BindingDetailed d = new BindingDetailed();
            d.userId = String.valueOf(b.userId);
            d.channelId = String.valueOf(b.channelId);
            d.userName = (jda != null && jda.getUserById(b.userId) != null)
                    ? jda.getUserById(b.userId).getName()
                    : "(Unknown)";
            d.channelName = (jda != null && jda.getVoiceChannelById(b.channelId) != null)
                    ? jda.getVoiceChannelById(b.channelId).getName()
                    : "(Unknown)";
            d.enabled = b.enabled;
            return d;
        }).collect(Collectors.toList());
        return gson.toJson(detailed);
    }

    private static Object toggleBinding(Request req, Response res) {
        String userIdStr = req.queryParams("userId");
        String channelIdStr = req.queryParams("channelId");
        boolean enabled = Boolean.parseBoolean(req.queryParams("enabled"));
        long userId = Long.parseUnsignedLong(userIdStr);
        long channelId = Long.parseUnsignedLong(channelIdStr);
        WywozBindingManager.setBindingEnabled(userId, channelId, enabled);
        return "OK";
    }

    private static Object forceRestart(Request req, Response res) {
        try {
            res.redirect("/");
            String processInfo = ProcessHandle.current().info().toString();
            DiscordBot.forceRestart();
            Thread.sleep(1000);
            if (ProcessHandle.current().info().toString().equals(processInfo)) {
                res.status(500);
                return "Restart failed - process did not change";
            }
            return "Restart successful";
        } catch (Exception e) {
            res.status(500);
            return "Error during restart: " + e.getMessage();
        }
    }
}

