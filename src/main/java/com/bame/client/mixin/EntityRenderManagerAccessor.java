package com.bame.client.mixin;

import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.equipment.EquipmentModelLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Kill-Effekt Thanos Snap: Rüstungs-Texturen der Opfer-Figur (gleiche Ebenen wie das Vanilla-Rüstungs-Feature). */
@Mixin(EntityRenderManager.class)
public interface EntityRenderManagerAccessor {
    @Accessor("equipmentModelLoader")
    EquipmentModelLoader bame$getEquipmentModelLoader();
}
