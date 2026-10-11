package com.bame.client.mixin;

import com.bame.client.emotes.EmotePreviewHolder;
import com.bame.client.emotes.EmoteType;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntityRenderState.class)
public class PlayerEntityRenderStateMixin implements EmotePreviewHolder {

    @Unique
    private EmoteType bame$previewEmote;

    @Unique
    private float bame$previewTime;

    @Override
    public void bame$setPreviewEmote(EmoteType emote) {
        this.bame$previewEmote = emote;
    }

    @Override
    public EmoteType bame$getPreviewEmote() {
        return this.bame$previewEmote;
    }

    @Override
    public void bame$setPreviewTime(float time) {
        this.bame$previewTime = time;
    }

    @Override
    public float bame$getPreviewTime() {
        return this.bame$previewTime;
    }

    @Unique
    private String caeser$playerUsername;

    @Override
    public void caeser$setPlayerUsername(String username) {
        this.caeser$playerUsername = username;
    }

    @Override
    public String caeser$getPlayerUsername() {
        return this.caeser$playerUsername;
    }
}
