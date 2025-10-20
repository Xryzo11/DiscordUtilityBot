package com.xryzo11.discordbot.core;

import com.xryzo11.discordbot.DiscordBot;
import com.xryzo11.discordbot.musicBot.MusicBot;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
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
        scheduler.scheduleAtFixedRate(this::forcedUpdatePresence, 0, 20, TimeUnit.SECONDS);
        this.bot = DiscordBot.musicBot;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        defaultPresence();
    }

    private void setPresence(String message, String type) {
        if (!Objects.equals(lastPresenceMessage, message)) {
            if (Config.isDebugEnabled()) System.out.println("[PresenceManager] Setting presence: " + type + " " + message);
            lastPresenceMessage = message;
        }

        switch (type) {
            case "playing" -> jda.getPresence().setActivity(Activity.playing(message));
            case "listening" -> jda.getPresence().setActivity(Activity.listening(message));
            case "watching" -> jda.getPresence().setActivity(Activity.watching(message));
            case "streaming" -> jda.getPresence().setActivity(Activity.streaming(message, "https://twitch.tv/xryzo11"));
            default -> jda.getPresence().setActivity(Activity.playing(message));
        }
    }

    private void setStatus(String status) {
        status = status.toLowerCase();
        if (!status.equals(lastStatus)) {
            if (Config.isDebugEnabled()) System.out.println("[PresenceManager] Setting status: " + status);
            lastStatus = status;
        }

        switch (status) {
            case "online" -> jda.getPresence().setStatus(net.dv8tion.jda.api.OnlineStatus.ONLINE);
            case "idle" -> jda.getPresence().setStatus(net.dv8tion.jda.api.OnlineStatus.IDLE);
            case "dnd" -> jda.getPresence().setStatus(net.dv8tion.jda.api.OnlineStatus.DO_NOT_DISTURB);
            case "invisible", "offline" -> jda.getPresence().setStatus(net.dv8tion.jda.api.OnlineStatus.INVISIBLE);
            default -> jda.getPresence().setStatus(net.dv8tion.jda.api.OnlineStatus.ONLINE);
        }
    }

    private String getDefaultPresenceMessage() {
        return "the server 👀";
    }

    public void defaultPresence() {
        setPresence(getDefaultPresenceMessage(), "watching");
        setStatus("idle");
    }

    public void updatePresence() {
        updatePresence(false);
    }

    public void forcedUpdatePresence() {
        updatePresence(true);
    }

    private void updatePresence(boolean forced) {
        if (bot.currentTrack != null) {
            String formattedInfo = MusicBot.formatTrackInfo(bot.currentTrack);
            String[] parts = formattedInfo.split("\\]\\(<");
            String title = parts[0].substring(1);
            if (title.length() > 120) title = title.substring(0, 120) + "...";
            String presence = title + " 🎵";
            if (presence.equals(lastPresenceMessage) && !forced) return;
            setPresence(presence, "listening");
            setStatus("dnd");
        } else if (BotHolder.getJDA().getGuilds().stream().anyMatch(g -> g.getAudioManager().isConnected())) {
            String presence = "over the vc ✅";
            if (presence.equals(lastPresenceMessage) && !forced) return;
            setPresence(presence, "watching");
            setStatus("online");
        } else {
            String presence = getDefaultPresenceMessage();
            if (presence.equals(lastPresenceMessage) && !forced) return;
            defaultPresence();
        }
    }
}
