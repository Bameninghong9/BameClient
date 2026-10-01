package com.bame.client.mixin;

import com.bame.client.render.ItemEntityRenderStateAccessor;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public class ItemEntityRenderStateMixin implements ItemEntityRenderStateAccessor {
    @Unique
    private Item bame$item;

    @Override
    public Item bame$getItem() {
        return bame$item;
    }

    @Override
    public void bame$setItem(Item item) {
        this.bame$item = item;
    }
}
