package com.xryzo11.discordbot.listeners;

import com.xryzo11.discordbot.core.BotSettings;
import com.xryzo11.discordbot.core.Config;
import com.xryzo11.discordbot.misc.RoleRestorer;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class GuildJoinListener extends ListenerAdapter {

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        Member member = event.getMember();
        Guild guild = event.getGuild();

        if (BotSettings.isDebug()) System.out.println("[JoinListener] New member joined: " + member.getEffectiveName());

        RoleRestorer.restoreRole(event);
    }
}
