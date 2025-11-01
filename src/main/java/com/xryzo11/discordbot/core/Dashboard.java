package com.xryzo11.discordbot.core;

import static spark.Spark.*;
import com.google.gson.Gson;
import com.xryzo11.discordbot.DiscordBot;
import com.xryzo11.discordbot.misc.TempRoleManager;
import com.xryzo11.discordbot.misc.WywozBindingManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Role;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class Dashboard {
    public static void start() {
        Gson gson = new Gson();

        port(Config.getWebPort());
        staticFiles.location("/public");

        get("/web-auth", (req, res) -> {
            res.type("application/json");

            Map<String, Object> data = new HashMap<>();
            data.put("enabled", Config.isWebAuthEnabled());
            data.put("password", Config.getWebAuthPassword());

            return data;
        }, gson::toJson);

        get("/get-info", (req, res) -> {
            res.type("application/json");

            Map<String, Object> data = new HashMap<>();
            data.put("fullVersion", DiscordBot.fullVersion);
            data.put("lastRestart", DiscordBot.lastRestart);

            return data;
        }, gson::toJson);

        get("/wywoz-initial-status", (req, res) -> BotSettings.isWywozSmieciInitial() ? "enabled" : "disabled");
        get("/wywoz-status", (req, res) -> BotSettings.isWywozSmieciAuto() ? "enabled" : "disabled");
        post("/toggle-wywoz", (req, res) -> {
            boolean state = Boolean.parseBoolean(req.queryParams("status"));
            BotSettings.setWywozSmieci(state);
            res.redirect("/");
            System.out.println("[dashboard] Wywoz smieci toggled to: " + (state ? "ON" : "OFF"));
            return "Wywoz smieci toggled to: " + (state ? "ON" : "OFF");
        });

        get("/temp-role-initial-status", (req, res) -> BotSettings.isTempRoleInitial() ? "enabled" : "disabled");
        get("/temp-role-status", (req, res) -> BotSettings.isTempRoleAuto() ? "enabled" : "disabled");
        post("/toggle-temp-role-status", (req, res) -> {
            boolean state = Boolean.parseBoolean(req.queryParams("status"));
            BotSettings.setTempRole(state);
            res.redirect("/");
            System.out.println("[dashboard] Temporary role toggled to: " + (state ? "ON" : "OFF"));
            return "Temporary role toggled to: " + (state ? "ON" : "OFF");
        });

        post("/add-temp-role", (req, res) -> {
            String roleIdStr = req.queryParams("roleId");
            String channelIdStr = req.queryParams("channelId");
            long roleId = Long.parseUnsignedLong(roleIdStr);
            long channelId = Long.parseUnsignedLong(channelIdStr);
            TempRoleManager.addTempRole(roleId, channelId);
            return "OK";
        });

        post("/remove-temp-role", (req, res) -> {
            long roleId = Long.parseUnsignedLong(req.queryParams("roleId"));
            long channelId = Long.parseUnsignedLong(req.queryParams("channelId"));
            TempRoleManager.removeTempRole(roleId, channelId);
            return "OK";
        });

        post("/toggle-temp-role", (req, res) -> {
            String roleIdStr = req.queryParams("roleId");
            String channelIdStr = req.queryParams("channelId");
            boolean enabled = Boolean.parseBoolean(req.queryParams("enabled"));
            long roleId = Long.parseUnsignedLong(roleIdStr);
            long channelId = Long.parseUnsignedLong(channelIdStr);
            TempRoleManager.setRoleEnabled(roleId, channelId, enabled);
            return "OK";
        });

        get("/temp-roles-detailed", (req, res) -> {
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
            return new Gson().toJson(detailed);
        });

        get("/debug-status", (req, res) -> BotSettings.isDebug() ? "enabled" : "disabled");
        post("/toggle-debug", (req, res) -> {
            boolean state = Boolean.parseBoolean(req.queryParams("status"));
            BotSettings.setDebug(state);
            res.redirect("/");
            System.out.println("[dashboard] Debug mode toggled to: " + (state ? "ON" : "OFF"));
            return "Debug mode toggled to: " + (state ? "ON" : "OFF");
        });

        post("/add-binding", (req, res) -> {
            String userIdStr = req.queryParams("userId");
            String channelIdStr = req.queryParams("channelId");
            long userId = Long.parseUnsignedLong(userIdStr);
            long channelId = Long.parseUnsignedLong(channelIdStr);
            WywozBindingManager.addBinding(userId, channelId);
            return "OK";
        });

        post("/remove-binding", (req, res) -> {
            long userId = Long.parseUnsignedLong(req.queryParams("userId"));
            long channelId = Long.parseUnsignedLong(req.queryParams("channelId"));
            WywozBindingManager.removeBinding(userId, channelId);
            return "OK";
        });

        get("/bindings-detailed", (req, res) -> {
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
            return new Gson().toJson(detailed);
        });

        post("/toggle-binding", (req, res) -> {
            String userIdStr = req.queryParams("userId");
            String channelIdStr = req.queryParams("channelId");
            boolean enabled = Boolean.parseBoolean(req.queryParams("enabled"));
            long userId = Long.parseUnsignedLong(userIdStr);
            long channelId = Long.parseUnsignedLong(channelIdStr);
            WywozBindingManager.setBindingEnabled(userId, channelId, enabled);
            return "OK";
        });

        post("/force-restart", (req, res) -> {
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
        });
    }

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
}