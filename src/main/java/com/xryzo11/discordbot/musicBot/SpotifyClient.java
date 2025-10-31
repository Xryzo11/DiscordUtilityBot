package com.xryzo11.discordbot.musicBot;

import com.xryzo11.discordbot.core.Config;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.detailed.UnauthorizedException;
import se.michaelthelin.spotify.model_objects.credentials.ClientCredentials;
import se.michaelthelin.spotify.requests.authorization.client_credentials.ClientCredentialsRequest;

import java.util.concurrent.Callable;

public final class SpotifyClient {
    private static SpotifyApi api;
    private static long expiresAtMs = 0L;

    private SpotifyClient() {}

    public static synchronized SpotifyApi getApi() {
        ensureToken();
        return api;
    }

    public static synchronized <T> T withAuthRetry(Callable<T> action) throws Exception {
        try {
            ensureToken();
            return action.call();
        } catch (UnauthorizedException e) {
            forceRefresh();
            return action.call();
        }
    }

    private static synchronized void ensureToken() {
        if (api == null) {
            String clientId = Config.getSpotifyClientId();
            String clientSecret = Config.getSpotifyClientSecret();
            if (clientId == null || clientId.isBlank() || "YOUR_SPOTIFY_CLIENT_ID_HERE".equals(clientId)) {
                throw new IllegalStateException("Spotify Client ID is not configured");
            }
            if (clientSecret == null || clientSecret.isBlank() || "YOUR_SPOTIFY_CLIENT_SECRET_HERE".equals(clientSecret)) {
                throw new IllegalStateException("Spotify Client Secret is not configured");
            }
            api = new SpotifyApi.Builder()
                    .setClientId(clientId)
                    .setClientSecret(clientSecret)
                    .build();
        }
        if (System.currentTimeMillis() >= expiresAtMs - 10_000) {
            refreshToken();
        }
    }

    public static synchronized void forceRefresh() {
        if (api == null) ensureToken();
        refreshToken();
    }

    private static void refreshToken() {
        try {
            ClientCredentialsRequest req = api.clientCredentials().build();
            ClientCredentials creds = req.execute();
            api.setAccessToken(creds.getAccessToken());
            expiresAtMs = System.currentTimeMillis() + (creds.getExpiresIn() * 1000L);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to obtain Spotify access token", e);
        }
    }
}
