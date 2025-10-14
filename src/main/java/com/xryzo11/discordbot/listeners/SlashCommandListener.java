package com.xryzo11.discordbot.listeners;

import com.xryzo11.discordbot.core.SlashCommands;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SlashCommandListener extends ListenerAdapter {
    private final ExecutorService commandExecutor = Executors.newCachedThreadPool();
    SlashCommands slashCommands = new SlashCommands();

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        commandExecutor.submit(() -> slashCommands.handleCommand(event));
    }
}