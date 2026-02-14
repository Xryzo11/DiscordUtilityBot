package com.xryzo11.discordbot.musicBot;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

public class QueuedTrack {
    private final AudioTrack track;
    private final String userId;

    public QueuedTrack(AudioTrack track, String userId) {
        this.track = track;
        this.userId = userId;
    }

    public AudioTrack getTrack() {
        return track;
    }

    public String getUserId() {
        return userId;
    }
}
