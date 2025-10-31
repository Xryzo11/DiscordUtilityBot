package com.xryzo11.discordbot.utils.listeners;

import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class ReactionListener extends ListenerAdapter {
    public static boolean awaitingConfirmation = false;
    public static boolean confirmed = false;

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        if (event.getUser().isBot()) {
            confirmed = false;
            return;
        }

        if (event.getUser() == null) {
            return;
        }

        if (awaitingConfirmation) {
            if (event.getMessageAuthorId().equals(event.getJDA().getSelfUser().getId())) {
                if (event.getEmoji().getName().equals("✅")) {
                    confirmed = true;
                    awaitingConfirmation = false;
                    return;
                }
            }
        }
    }
}
