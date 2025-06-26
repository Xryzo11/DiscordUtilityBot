package com.xryzo11.discordbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;

public class DiscordBot {
    public static void main(String[] args) throws Exception {
        JDA jda = JDABuilder.createDefault("MTE0Njk1NTA2MjU0ODExOTU2Mw.GLhfzj._hnZysbjS-g-S1Qocph117_WcCgaKG9q5HdEao")
                .addEventListeners(new VoiceJoinListener())
                .build();
        WywozBindingManager.loadBindings();
        BotHolder.setJDA(jda);
        Dashboard.start();
    }

    public static class VoiceJoinListener extends ListenerAdapter {
        @Override
        public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
            if (event.getChannelJoined() != null) {
                String user = event.getMember().getEffectiveName();
                String channel = event.getChannelJoined().getName();
                long channelId = event.getChannelJoined().getIdLong();
                long userId = event.getMember().getUser().getIdLong();
                if (BotSettings.isDebug()) {
                    System.out.println(user + " joined voice channel: " + channel);
                }
                if (BotSettings.isWywozSmieci()) {
                    if (WywozBindingManager.isBound(userId, channelId)) {
                        Member member = event.getGuild().getMemberById(userId);
                        Guild guild = event.getGuild();
                        if (member != null && guild != null) {
                            guild.kickVoiceMember(member).queue();
                            System.out.println("Wywoz smieci (" + user + " | " + channel + ")");
                        } else {
                            System.out.println("Blad z wywozem smieci  (" + user + " | " + channel + ")");
                        }
                    }
                }
            }
        }
    }
}
