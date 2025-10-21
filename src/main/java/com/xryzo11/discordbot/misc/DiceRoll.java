package com.xryzo11.discordbot.misc;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.util.Random;

public class DiceRoll {
    private static final Random rand = new Random();

    public static void rollDice(SlashCommandInteractionEvent event, int sides, int count) {
        event.getHook().editOriginal("Rolling " + count + "d" + sides + "...").queue();
        int total = 0;
        StringBuilder rolls = new StringBuilder();
        for (int i = 0; i < count; i++) {
            int roll = rand.nextInt(sides) + 1;
            total += roll;
            rolls.append("__Roll ").append(i + 1).append("__: *").append(roll).append("*");
            if (i < count - 1) {
                rolls.append(",\n");
            }
        }
        rolls.append("\n***Rolls***: __").append(count).append("d").append(sides).append("__");
        rolls.append("\n***Total***: __").append(total).append("__");
        event.getHook().editOriginal(rolls.toString()).queue();
    }
}
