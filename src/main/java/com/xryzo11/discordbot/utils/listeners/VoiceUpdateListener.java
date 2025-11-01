package com.xryzo11.discordbot.utils.listeners;

import com.xryzo11.discordbot.misc.TempRoleManager;
import com.xryzo11.discordbot.misc.WywozBindingManager;
import com.xryzo11.discordbot.core.BotSettings;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class VoiceUpdateListener extends ListenerAdapter {
    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        if (event.getChannelJoined() != null) {
            String user = event.getMember().getEffectiveName();
            String channel = event.getChannelJoined().getName();
            long channelId = event.getChannelJoined().getIdLong();
            long userId = event.getMember().getUser().getIdLong();

            if (BotSettings.isDebug()) {
                System.out.println("[VoiceUpdateListener] " + user + " joined voice channel: " + channel);
            }

            if (BotSettings.isWywozSmieci()) {
                if (WywozBindingManager.isBound(userId, channelId)) {
                    WywozBindingManager.execute(event, userId, user, channel);
                }
            }

            if (BotSettings.isTempRole()) {
                if (TempRoleManager.isBound(channelId)) {
                    TempRoleManager.execute(event, userId, user, channel, true);
                }
            }
        }

        if (event.getChannelLeft() != null) {
            String user = event.getMember().getEffectiveName();
            String channel = event.getChannelLeft().getName();
            long channelId = event.getChannelLeft().getIdLong();
            long userId = event.getMember().getUser().getIdLong();

            if (BotSettings.isDebug()) {
                System.out.println("[VoiceUpdateListener] " + user + " left voice channel: " + channel);
            }

            if (BotSettings.isTempRole()) {
                if (TempRoleManager.isBound(channelId)) {
                    TempRoleManager.execute(event, userId, user, channel, false);
                }
            }
        }
    }
}