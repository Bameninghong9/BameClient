package com.bame.client.emotes;

public interface EmotePreviewHolder {
    void bame$setPreviewEmote(EmoteType emote);
    EmoteType bame$getPreviewEmote();
    void bame$setPreviewTime(float time);
    float bame$getPreviewTime();

    void caeser$setPlayerUsername(String username);
    String caeser$getPlayerUsername();
}
