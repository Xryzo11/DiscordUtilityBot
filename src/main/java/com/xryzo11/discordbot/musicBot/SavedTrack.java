package com.xryzo11.discordbot.musicBot;

public class SavedTrack {
    private final String name;
    private final String url;

    public SavedTrack(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }
}
