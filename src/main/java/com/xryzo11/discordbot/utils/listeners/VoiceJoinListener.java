package com.xryzo11.discordbot.utils.listeners;

import com.xryzo11.discordbot.misc.WywozBindingManager;
import com.xryzo11.discordbot.core.BotSettings;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class VoiceJoinListener extends ListenerAdapter {
    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        if (event.getChannelJoined() != null) {
            String user = event.getMember().getEffectiveName();
            String channel = event.getChannelJoined().getName();
            long channelId = event.getChannelJoined().getIdLong();
            long userId = event.getMember().getUser().getIdLong();
            if (BotSettings.isDebug()) {
                System.out.println("[wywozBindingManager]" + user + " joined voice channel: " + channel);
            }
            if (BotSettings.isWywozSmieci()) {
                if (WywozBindingManager.isBound(userId, channelId)) {
                    Member member = event.getGuild().getMemberById(userId);
                    Guild guild = event.getGuild();
                    if (member != null && guild != null) {
                        guild.kickVoiceMember(member).queue();
                        System.out.println("[wywozBindingManager] Wywoz smieci (" + user + " | " + channel + ")");
                    } else {
                        System.out.println("[wywozBindingManager] Blad z wywozem smieci  (" + user + " | " + channel + ")");
                    }
                }
            }
        }
    }
}