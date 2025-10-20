package com.xryzo11.discordbot.core;

import com.xryzo11.discordbot.DiscordBot;
import com.xryzo11.discordbot.musicBot.MusicBot;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PresenceManager extends ListenerAdapter {
    private final JDA jda;
    private final ScheduledExecutorService scheduler;
    private final MusicBot bot;
    private String lastPresenceMessage = "";
    private String lastStatus = "";

    public PresenceManager(JDA jda) {
        this.jda = jda;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
//        scheduler.scheduleAtFixedRate(this::updatePresence, 0, 15, TimeUnit.SECONDS);
        this.bot = DiscordBot.musicBot;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        defaultPresence();
    }

    public void setPresence(String message, String type) {
        String newPresence = type + ":" + message;
//        if (!newPresence.equals(lastPresenceMessage)) {
//            if (Config.isDebugEnabled()) System.out.println("[PresenceManager] Setting presence: " + type + " " + message);
//            lastPresenceMessage = newPresence;
//        }
        if (Config.isDebugEnabled()) System.out.println("[PresenceManager] Setting presence: " + type + " " + message);
        lastPresenceMessage = newPresence;
        switch (type) {
            case "playing" -> jda.getPresence().setActivity(Activity.playing(message));
            case "listening" -> jda.getPresence().setActivity(Activity.listening(message));
            case "watching" -> jda.getPresence().setActivity(Activity.watching(message));
            case "streaming" -> jda.getPresence().setActivity(Activity.streaming(message, "https://twitch.tv/xryzo11"));
            default -> jda.getPresence().setActivity(Activity.playing(message));
        }
    }

    public void setStatus(String status) {
        String newStatus = status.toLowerCase();
//        if (!newStatus.equals(lastStatus)) {
//            if (Config.isDebugEnabled()) System.out.println("[PresenceManager] Setting status: " + status);
//            lastStatus = newStatus;
//        }
        if (Config.isDebugEnabled()) System.out.println("[PresenceManager] Setting status: " + status);
        lastStatus = newStatus;
        switch (status.toLowerCase()) {
            case "online" -> jda.getPresence().setStatus(net.dv8tion.jda.api.OnlineStatus.ONLINE);
            case "idle" -> jda.getPresence().setStatus(net.dv8tion.jda.api.OnlineStatus.IDLE);
            case "dnd" -> jda.getPresence().setStatus(net.dv8tion.jda.api.OnlineStatus.DO_NOT_DISTURB);
            case "invisible", "offline" -> jda.getPresence().setStatus(net.dv8tion.jda.api.OnlineStatus.INVISIBLE);
            default -> jda.getPresence().setStatus(net.dv8tion.jda.api.OnlineStatus.ONLINE);
        }
    }

    public void defaultPresence() {
        setPresence("the server \uD83D\uDC40", "watching");
        setStatus("online");
    }

    public void updatePresence() {
        if (bot.currentTrack != null) {
            String formattedInfo = MusicBot.formatTrackInfo(bot.currentTrack);
            String[] parts = formattedInfo.split("\\]\\(<");
            String title = parts[0].substring(1);
            if (title.length() > 120) title = title.substring(0, 120) + "...";
            setPresence(title + " 🎵", "listening");
        } else if (BotHolder.getJDA().getGuilds().stream().anyMatch(g -> g.getAudioManager().isConnected())) {
            String newPresence = "over the vc ✅";
            setPresence(newPresence, "watching");
        } else {
            defaultPresence();
        }
    }
}
