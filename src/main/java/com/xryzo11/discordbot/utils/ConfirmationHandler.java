package com.xryzo11.discordbot.utils;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ConfirmationHandler {
    private final Message message;
    private final User user;
    private final CompletableFuture<ConfirmationResult> future;
    private final long timeoutMillis;

    private static final String CONFIRM_EMOJI = "✅";
    private static final String CANCEL_EMOJI = "❌";

    public ConfirmationHandler(Message message, User user, long timeoutSeconds) {
        this.message = message;
        this.user = user;
        this.future = new CompletableFuture<>();
        this.timeoutMillis = TimeUnit.SECONDS.toMillis(timeoutSeconds);

        message.addReaction(Emoji.fromUnicode(CONFIRM_EMOJI)).queue();
        message.addReaction(Emoji.fromUnicode(CANCEL_EMOJI)).queue();

        setupTimeout();
    }

    private void setupTimeout() {
        CompletableFuture.delayedExecutor(timeoutMillis, TimeUnit.MILLISECONDS)
                .execute(() -> {
                    if (!future.isDone()) {
                        future.complete(ConfirmationResult.TIMEOUT);
                    }
                });
    }

    public void handleReaction(MessageReactionAddEvent event) {
        if (event.getUser() == null || event.getUser().isBot()) {
            return;
        }

        if (!event.getMessageId().equals(message.getId())) {
            return;
        }

        if (!event.getUser().getId().equals(user.getId())) {
            return;
        }

        String emoji = event.getEmoji().getName();

        if (emoji.equals(CONFIRM_EMOJI)) {
            future.complete(ConfirmationResult.CONFIRMED);
        } else if (emoji.equals(CANCEL_EMOJI)) {
            future.complete(ConfirmationResult.CANCELLED);
        }
    }

    public CompletableFuture<ConfirmationResult> awaitConfirmation() {
        return future;
    }

    public enum ConfirmationResult {
        CONFIRMED,
        CANCELLED,
        TIMEOUT
    }
}
