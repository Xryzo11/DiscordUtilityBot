package com.xryzo11.discordbot;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class RockPaperScissors {
    public static boolean isActive = false;
    public static Member player1;
    public static Member player2;
    private static String p1choice;
    private static String p2choice;
    public static MessageChannelUnion channel;
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> timeOut;

    public static void challenge(Member challenger, Member opponent, MessageChannelUnion channel) {
        nullify();
        player1 = challenger;
        player2 = opponent;
        RockPaperScissors.channel = channel;
        isActive = true;
        channel.sendMessage(String.format(player1.getAsMention() + "has challenged" + player2.getAsMention() + "to a game of Rock-Paper-Scissors! Make your choice with **/rps-choose** within 2 minutes.")).queue();
        timeOut = scheduler.schedule(() -> {
            if (!isActive) return;
            nullify();
            channel.sendMessage(String.format("The challenge between" + player1.getAsMention() + "and" + player2.getAsMention() + "timed out.")).queue();
        }, 2, TimeUnit.MINUTES);
    }

    public static void makeChoice(Member player, String choice) {
        if (player == player1) {
            p1choice = choice.toLowerCase();
        } else if (player == player2) {
            p2choice = choice.toLowerCase();
        }
        attemptResolve();
    }

    private static void attemptResolve() {
        if (p1choice != null && p2choice != null) {
            resolve();
        }
    }

    private static void resolve() {
        switch (p1choice) {
            case "rock":
                p1choice = "\uD83E\uDEA8";
                break;
            case "paper":
                p1choice = "\uD83D\uDCC4";
                break;
            case "scissors":
                p1choice = "✂️";
                break;
            default:
                channel.sendMessage(player1.getAsMention() + ", your choice is invalid!").queue();
                return;
        }
        switch (p2choice) {
            case "rock":
                p2choice = "\uD83E\uDEA8";
                break;
            case "paper":
                p2choice = "\uD83D\uDCC4";
                break;
            case "scissors":
                p2choice = "✂️";
                break;
            default:
                channel.sendMessage(player2.getAsMention() + ", your choice is invalid!").queue();
                return;
        }
        if (p1choice.equals(p2choice)) {
            channel.sendMessage("Rock Paper Scissors game between " + player1.getAsMention() + " and " + player2.getAsMention() + "\n" + player1.getAsMention() + ": " + p1choice + "\n" + player2.getAsMention() + ": " + p2choice + "\n**Tie!**").queue();
        } else {
            if ((p1choice.equals("\uD83E\uDEA8") && p2choice.equals("✂️")) || (p1choice.equals("\uD83D\uDCC4") && p2choice.equals("\uD83E\uDEA8")) || (p1choice.equals("✂️") && p2choice.equals("\uD83D\uDCC4"))) {
                channel.sendMessage("Rock-Paper-Scissors game between " + player1.getAsMention() + " and " + player2.getAsMention() + "\n" + player1.getAsMention() + ": " + p1choice + "\n" + player2.getAsMention() + ": " + p2choice + "\n**" + player1.getAsMention() +" wins!**").queue();
            } else {
                channel.sendMessage("Rock-Paper-Scissors game between " + player1.getAsMention() + " and " + player2.getAsMention() + "\n" + player1.getAsMention() + ": " + p1choice + "\n" + player2.getAsMention() + ": " + p2choice + "\n**" + player2.getAsMention() +" wins!**").queue();
            }
            nullify();
        }
    }

    public static void cancelGame() {
        channel.sendMessage("Rock Paper Scissors game between " + player1.getAsMention() + " and " + player2.getAsMention() + " has been cancelled!").queue();
        nullify();
    }

    private static void nullify() {
        if (timeOut != null && !timeOut.isDone()) timeOut.cancel(false);
        isActive = false;
        player1 = null;
        player2 = null;
        p1choice = null;
        p2choice = null;
        channel = null;
    }
}
