package com.xryzo11.discordbot.core;

import com.xryzo11.discordbot.core.web.AuthHandler;
import com.xryzo11.discordbot.core.web.BotSettingsHandler;
import com.xryzo11.discordbot.core.web.PlayerHandler;
import com.xryzo11.discordbot.core.web.SessionManager;

import static spark.Spark.*;

public class Dashboard {

    public static void start() {
        SessionManager.loadSessions();

        port(Config.getWebPort());
        staticFiles.location("/public");

        setupAuthenticationFilters();

        AuthHandler.registerRoutes();
        PlayerHandler.registerRoutes();
        BotSettingsHandler.registerRoutes();
    }

    private static void setupAuthenticationFilters() {
        before("/toggle-*", (req, res) -> {
            if (!AuthHandler.isAuthenticated(req)) {
                res.status(401);
                halt(401, "{\"error\": \"Unauthorized\"}");
            }
        });

        before("/add-*", (req, res) -> {
            if (!AuthHandler.isAuthenticated(req)) {
                res.status(401);
                halt(401, "{\"error\": \"Unauthorized\"}");
            }
        });

        before("/remove-*", (req, res) -> {
            if (!AuthHandler.isAuthenticated(req)) {
                res.status(401);
                halt(401, "{\"error\": \"Unauthorized\"}");
            }
        });

        before("/force-restart", (req, res) -> {
            if (!AuthHandler.isAuthenticated(req)) {
                res.status(401);
                halt(401, "{\"error\": \"Unauthorized\"}");
            }
        });

        before("/*-status", (req, res) -> {
            if (!AuthHandler.isAuthenticated(req)) {
                res.status(401);
                halt(401, "{\"error\": \"Unauthorized\"}");
            }
        });

        before("/*-detailed", (req, res) -> {
            if (!AuthHandler.isAuthenticated(req)) {
                res.status(401);
                halt(401, "{\"error\": \"Unauthorized\"}");
            }
        });

        before("/music/*", (req, res) -> {
            String authType = Config.getWebAuthType();
        });
    }
}

