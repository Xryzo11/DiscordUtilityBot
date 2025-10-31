package com.xryzo11.discordbot.misc;

import com.xryzo11.discordbot.core.BotHolder;
import com.xryzo11.discordbot.core.BotSettings;
import com.xryzo11.discordbot.core.Config;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

// temporary class for migrating to a new server by restoring roles based on old server roles

public class RoleRestorer {
    static JDA jda = BotHolder.getJDA();
    static Guild guildOld = jda.getGuildById("670777284675895306");
    static Guild guildNew = jda.getGuildById("1424775800896360491");

    static Role kozakOld = jda.getRoleById(670787571768360971L);
    static Role dalnOld = jda.getRoleById(986801833207017583L);
    static Role lamusyOld = jda.getRoleById(766425322560028704L);
    static Role noobgiOld = jda.getRoleById(790919541751283732L);
    static Role kurwyOld = jda.getRoleById(790919634341724181L);
    static Role smolppOld = jda.getRoleById(670787317396406292L);
    static Role kartapedalaOld = jda.getRoleById(670805972830388247L);
    static Role gejeOld = jda.getRoleById(747586692621402133L);

    static Role lamusyNew = jda.getRoleById(1424775801265721534L);
    static Role noobgiNew = jda.getRoleById(1424775800896360497L);
    static Role kurwyNew = jda.getRoleById(1424775800896360496L);
    static Role smolppNew = jda.getRoleById(1424775800896360495L);
    static Role kartapedalaNew = jda.getRoleById(1424775800896360494L);
    static Role gejeNew = jda.getRoleById(1424775800896360493L);


    public static void restoreRole(GuildMemberJoinEvent event) {
        Member member = event.getMember();
        Member memberOld = guildOld.retrieveMemberById(member.getId()).complete();
        if (memberOld == null) {
            if (BotSettings.isDebug()) System.out.println("[RoleRestorer] User " + member.getUser().getAsTag() + " not found in old server.");
            return;
        }
        if (!member.getRoles().isEmpty()) {
            if (BotSettings.isDebug()) System.out.println("[RoleRestorer] User " + member.getUser().getAsTag() + " already has a role in the new server. Skipping role assignment.");
            return;
        }
        if (memberOld.getRoles().contains(kozakOld)) {
            guildNew.addRoleToMember(member, lamusyNew).queue();
            if (BotSettings.isDebug()) System.out.println("[RoleRestorer] User " + member.getUser().getAsTag() + " assigned role: Lamusy");
        } else if (memberOld.getRoles().contains(dalnOld)) {
            guildNew.addRoleToMember(member, lamusyNew).queue();
            if (BotSettings.isDebug()) System.out.println("[RoleRestorer] User " + member.getUser().getAsTag() + " assigned role: Lamusy");
        } else if (memberOld.getRoles().contains(lamusyOld)) {
            guildNew.addRoleToMember(member, lamusyNew).queue();
            if (BotSettings.isDebug()) System.out.println("[RoleRestorer] User " + member.getUser().getAsTag() + " assigned role: Lamusy");
        } else if (memberOld.getRoles().contains(noobgiOld)) {
            guildNew.addRoleToMember(member, noobgiNew).queue();
            if (BotSettings.isDebug()) System.out.println("[RoleRestorer] User " + member.getUser().getAsTag() + " assigned role: Noobgi");
        } else if (memberOld.getRoles().contains(kurwyOld)) {
            guildNew.addRoleToMember(member, kurwyNew).queue();
            if (BotSettings.isDebug()) System.out.println("[RoleRestorer] User " + member.getUser().getAsTag() + " assigned role: Kurwy");
        } else if (memberOld.getRoles().contains(smolppOld)) {
            guildNew.addRoleToMember(member, smolppNew).queue();
            if (BotSettings.isDebug()) System.out.println("[RoleRestorer] User " + member.getUser().getAsTag() + " assigned role: Smolpp");
        } else if (memberOld.getRoles().contains(kartapedalaOld)) {
            guildNew.addRoleToMember(member, kartapedalaNew).queue();
            if (BotSettings.isDebug()) System.out.println("[RoleRestorer] User " + member.getUser().getAsTag() + " assigned role: Karta Pedala");
        } else if (memberOld.getRoles().contains(gejeOld)) {
            guildNew.addRoleToMember(member, gejeNew).queue();
            if (BotSettings.isDebug()) System.out.println("[RoleRestorer] User " + member.getUser().getAsTag() + " assigned role: Geje");
        } else {
            if (BotSettings.isDebug()) System.out.println("[RoleRestorer] User " + member.getUser().getAsTag() + " has no matching roles to assign.");
        }
    }

    public static void restoreRole(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        Member member = event.getMember();
        Member memberOld = guildOld.retrieveMemberById(member.getId()).complete();
        if (memberOld == null) {
            event.getHook().editOriginal("You are not found in the old server. Cannot restore roles.").queue();
            if (BotSettings.isDebug()) System.out.println("[RoleRestorer] User " + member.getUser().getAsTag() + " not found in old server.");
            return;
        }
        if (!member.getRoles().isEmpty()) {
            event.getHook().editOriginal("You already have a role in the new server. Skipping role assignment.").queue();
            if (BotSettings.isDebug()) System.out.println("[RoleRestorer] User " + member.getUser().getAsTag() + " already has a role in the new server. Skipping role assignment.");
            return;
        }
        if (memberOld.getRoles().contains(kozakOld)) {
            guildNew.addRoleToMember(member, lamusyNew).queue();
            event.getHook().editOriginal("Assigned highest owned role: Lamusy").queue();
            if (BotSettings.isDebug()) System.out.println("[RoleRestorer] User " + member.getUser().getAsTag() + " assigned role: Lamusy");
        } else if (memberOld.getRoles().contains(dalnOld)) {
            guildNew.addRoleToMember(member, lamusyNew).queue();
            event.getHook().editOriginal("Assigned highest owned role: Lamusy").queue();
            if (BotSettings.isDebug()) System.out.println("[RoleRestorer] User " + member.getUser().getAsTag() + " assigned role: Lamusy");
        } else if (memberOld.getRoles().contains(lamusyOld)) {
            guildNew.addRoleToMember(member, lamusyNew).queue();
            event.getHook().editOriginal("Assigned highest owned role: Lamusy").queue();
            if (BotSettings.isDebug()) System.out.println("[RoleRestorer] User " + member.getUser().getAsTag() + " assigned role: Lamusy");
        } else if (memberOld.getRoles().contains(noobgiOld)) {
            guildNew.addRoleToMember(member, noobgiNew).queue();
            event.getHook().editOriginal("Assigned highest owned role: Noobgi").queue();
            if (BotSettings.isDebug()) System.out.println("[RoleRestorer] User " + member.getUser().getAsTag() + " assigned role: Noobgi");
        } else if (memberOld.getRoles().contains(kurwyOld)) {
            guildNew.addRoleToMember(member, kurwyNew).queue();
            event.getHook().editOriginal("Assigned highest owned role: Kurwy").queue();
            if (BotSettings.isDebug()) System.out.println("[RoleRestorer] User " + member.getUser().getAsTag() + " assigned role: Kurwy");
        } else if (memberOld.getRoles().contains(smolppOld)) {
            guildNew.addRoleToMember(member, smolppNew).queue();
            event.getHook().editOriginal("Assigned highest owned role: Smolpp").queue();
            if (BotSettings.isDebug()) System.out.println("[RoleRestorer] User " + member.getUser().getAsTag() + " assigned role: Smolpp");
        } else if (memberOld.getRoles().contains(kartapedalaOld)) {
            guildNew.addRoleToMember(member, kartapedalaNew).queue();
            event.getHook().editOriginal("Assigned highest owned role: Karta Pedala").queue();
            if (BotSettings.isDebug()) System.out.println("[RoleRestorer] User " + member.getUser().getAsTag() + " assigned role: Karta Pedala");
        } else if (memberOld.getRoles().contains(gejeOld)) {
            guildNew.addRoleToMember(member, gejeNew).queue();
            event.getHook().editOriginal("Assigned highest owned role: Geje").queue();
            if (BotSettings.isDebug()) System.out.println("[RoleRestorer] User " + member.getUser().getAsTag() + " assigned role: Geje");
        } else {
            event.getHook().editOriginal("You have no matching roles to assign.").queue();
            if (BotSettings.isDebug()) System.out.println("[RoleRestorer] User " + member.getUser().getAsTag() + " has no matching roles to assign.");
        }
    }
}
