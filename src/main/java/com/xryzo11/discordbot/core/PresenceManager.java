package com.xryzo11.discordbot.core;

import com.xryzo11.discordbot.DiscordBot;
import com.xryzo11.discordbot.musicBot.MusicBot;
import com.xryzo11.discordbot.utils.enums.PresenceStatus;
import com.xryzo11.discordbot.utils.enums.PresenceActivity;
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

    private final String idleMessage = "Chilling in the server 👀";
    private final String vcMessage = "Watching over the vc ✅";
    private final String musicMessage = "Listening to ";

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

    private void setPresence(String message, PresenceActivity type) {
        if (!Objects.equals(lastPresenceMessage, message)) {
            if (Config.isDebugEnabled()) System.out.println("[PresenceManager] Setting presence: " + type + " " + message);
            lastPresenceMessage = message;
        }

        switch (type) {
            case PLAYING -> jda.getPresence().setActivity(Activity.playing(message));
            case LISTENING -> jda.getPresence().setActivity(Activity.listening(message));
            case WATCHING -> jda.getPresence().setActivity(Activity.watching(message));
            case STREAMING -> jda.getPresence().setActivity(Activity.streaming(message, "https://twitch.tv/xryzo11"));
        }
    }

    private void setStatus(PresenceStatus status) {
        String statusStr = status.toString();
        if (!statusStr.equals(lastStatus)) {
            if (Config.isDebugEnabled()) System.out.println("[PresenceManager] Setting status: " + status);
            lastStatus = statusStr;
        }

        switch (status) {
            case ONLINE -> jda.getPresence().setStatus(net.dv8tion.jda.api.OnlineStatus.ONLINE);
            case IDLE -> jda.getPresence().setStatus(net.dv8tion.jda.api.OnlineStatus.IDLE);
            case DND -> jda.getPresence().setStatus(net.dv8tion.jda.api.OnlineStatus.DO_NOT_DISTURB);
            case INVISIBLE -> jda.getPresence().setStatus(net.dv8tion.jda.api.OnlineStatus.INVISIBLE);
        }
    }

    public void defaultPresence() {
        setPresence(idleMessage, PresenceActivity.WATCHING);
        setStatus(PresenceStatus.IDLE);
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
            String presence =  musicMessage + title + " 🎵";
            if (presence.equals(lastPresenceMessage) && !forced) return;
            setPresence(presence, PresenceActivity.LISTENING);
            setStatus(PresenceStatus.DND);
        } else if (BotHolder.getJDA().getGuilds().stream().anyMatch(g -> g.getAudioManager().isConnected())) {
            String presence = vcMessage;
            if (presence.equals(lastPresenceMessage) && !forced) return;
            setPresence(presence, PresenceActivity.WATCHING);
            setStatus(PresenceStatus.ONLINE);
        } else {
            String presence = idleMessage;
            if (presence.equals(lastPresenceMessage) && !forced) return;
            defaultPresence();
        }
    }
}
