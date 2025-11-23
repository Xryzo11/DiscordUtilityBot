package com.xryzo11.discordbot.utils.listeners;

import com.xryzo11.discordbot.musicBot.MusicBot;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;

import java.util.List;
import java.util.stream.Collectors;

public class AutoCompleteListener extends ListenerAdapter {
    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (event.getName().equals("add") && event.getFocusedOption().getName().equals("track")) {
            String userInput = event.getFocusedOption().getValue().toLowerCase();

            List<Command.Choice> suggestions = MusicBot.savedTracks.stream()
                    .filter(track -> track.getName().toLowerCase().contains(userInput))
                    .limit(25)
                    .map(track -> new Command.Choice(track.getName(), track.getName()))
                    .collect(Collectors.toList());

            event.replyChoices(suggestions).queue();
        }
    }
}
