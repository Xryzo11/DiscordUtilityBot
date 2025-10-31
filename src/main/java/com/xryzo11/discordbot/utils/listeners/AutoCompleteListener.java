package com.xryzo11.discordbot.utils.listeners;

import com.xryzo11.discordbot.core.Config;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class AutoCompleteListener extends ListenerAdapter {
    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (event.getName().equals("add") && event.getFocusedOption().getName().equals("track")) {
            String userInput = event.getFocusedOption().getValue();
            File preloadedDir = new File(Config.getPreloadedDirectory());

            List<Command.Choice> choices = Arrays.stream(preloadedDir.listFiles())
                    .filter(File::isFile)
                    .map(file -> file.getName())
                    .map(name -> {
                        int dotIndex = name.lastIndexOf('.');
                        return (dotIndex > 0) ? name.substring(0, dotIndex) : name;
                    })
                    .map(name -> {
                        int idIndex = name.indexOf(" [(");
                        return (idIndex > 0) ? name.substring(0, idIndex) : name;
                    })
                    .distinct()
                    .filter(name -> name.toLowerCase().startsWith(userInput.toLowerCase()))
                    .map(name -> new Command.Choice(name, name))
                    .toList();

            event.replyChoices(choices).queue();

        }
    }
}
