package com.xryzo11.discordbot.utils.listeners;

import com.xryzo11.discordbot.utils.ConfirmationHandler;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ReactionListener extends ListenerAdapter {
    private static final Map<String, ConfirmationHandler> activeConfirmations = new ConcurrentHashMap<>();

    public static void registerConfirmation(String messageId, ConfirmationHandler handler) {
        activeConfirmations.put(messageId, handler);
    }

    public static void unregisterConfirmation(String messageId) {
        activeConfirmations.remove(messageId);
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        ConfirmationHandler handler = activeConfirmations.get(event.getMessageId());
        if (handler != null) {
            handler.handleReaction(event);
        }
    }
}
