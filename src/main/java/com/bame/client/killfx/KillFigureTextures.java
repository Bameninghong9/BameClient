package com.bame.client.killfx;

import com.bame.client.mixin.EntityRenderManagerAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.client.render.entity.equipment.EquipmentModelLoader;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Texturen der Snap-Figur: Sichtbarkeits-Masken (Alpha) von Skin- und Rüstungs-Texturen und die Rüstungs-Ebenen eines
 * Opfers (Textur + Färbung, wie das Vanilla-Rüstungs-Feature). Masken werden einmal pro Textur gelesen und
 * zwischengespeichert (keine Textur-Erzeugung, kein Lesen pro Frame).
 */
final class KillFigureTextures {

    /** Eine Rüstungs-Ebene: Platz (0 Kopf .. 3 Füße), Textur, Färbung (ARGB, -1 = ungefärbt). */
    record ArmorLayer(int armorSlot, Identifier texture, int color) {}

    private static final int MAX_MASKS = 24;
    /** Gelesene Maske (Breite x Höhe der Textur). */
    private record MaskData(boolean[] data, int width, int height) {}

    private static final Map<Identifier, Optional<MaskData>> MASKS = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Identifier, Optional<MaskData>> eldest) {
            return size() > MAX_MASKS;
        }
    };

    private KillFigureTextures() {}

    /** Maske einer Textur ({@code null}, wenn sie nicht gelesen werden kann: dann gelten alle Texel als sichtbar). */
    static KillFigureMesh.Mask mask(MinecraftClient mc, Identifier id) {
        if (id == null) return null;
        Optional<MaskData> cached = MASKS.get(id);
        if (cached == null) {
            cached = Optional.ofNullable(read(mc, id));
            MASKS.put(id, cached);
        }
        if (cached.isEmpty()) return null;
        boolean[] data = cached.get().data();
        int w = cached.get().width(), h = cached.get().height();
        // Masken-Koordinaten beziehen sich auf die Modell-Texturgröße (64 x 64 bzw. 64 x 32); größere Texturen (HD) skalieren
        return (x, y) -> {
            int ix = Math.min(w - 1, x * w / 64);
            int iy = Math.min(h - 1, y * w / 64);
            return data[iy * w + ix];
        };
    }

    private static MaskData read(MinecraftClient mc, Identifier id) {
        try {
            Optional<Resource> res = mc.getResourceManager().getResource(id);
            if (res.isPresent()) {
                try (InputStream in = res.get().getInputStream(); NativeImage img = NativeImage.read(in)) {
                    return toMask(img);
                }
            }
            // Heruntergeladene Skins liegen nur als Textur im Speicher
            AbstractTexture tex = mc.getTextureManager().getTexture(id);
            if (tex instanceof NativeImageBackedTexture backed && backed.getImage() != null) {
                return toMask(backed.getImage());
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static MaskData toMask(NativeImage img) {
        int w = img.getWidth(), h = img.getHeight();
        if (w <= 0 || h <= 0 || w > 1024 || h > 1024) return null;
        boolean[] out = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[y * w + x] = (img.getColorArgb(x, y) >>> 24) > 16;
            }
        }
        return new MaskData(out, w, h);
    }

    /** Rüstungs-Ebenen (Kopf, Brust, Beine, Füße) wie im {@code EquipmentRenderer}; Gegenstände ohne Rüstungsmodell entfallen. */
    static List<ArmorLayer> armorLayers(MinecraftClient mc, ItemStack[] armor) {
        List<ArmorLayer> out = new ArrayList<>();
        if (armor == null) return out;
        EquipmentModelLoader loader;
        try {
            loader = ((EntityRenderManagerAccessor) mc.getEntityRenderDispatcher()).bame$getEquipmentModelLoader();
        } catch (Throwable t) {
            return out;
        }
        if (loader == null) return out;
        for (int slot = 0; slot < Math.min(4, armor.length); slot++) {
            ItemStack stack = armor[slot];
            if (stack == null || stack.isEmpty()) continue;
            EquippableComponent eq = stack.get(DataComponentTypes.EQUIPPABLE);
            if (eq == null || eq.assetId().isEmpty()) continue;
            EquipmentModel.LayerType type = slot == 2 ? EquipmentModel.LayerType.HUMANOID_LEGGINGS : EquipmentModel.LayerType.HUMANOID;
            List<EquipmentModel.Layer> layers;
            try {
                layers = loader.get(eq.assetId().get()).getLayers(type);
            } catch (Throwable t) {
                continue;
            }
            int dye = DyedColorComponent.getColor(stack, 0);
            for (EquipmentModel.Layer layer : layers) {
                if (layer.usePlayerTexture()) continue;
                int color;
                if (layer.dyeable().isPresent()) {
                    int undyed = layer.dyeable().get().colorWhenUndyed().map(ColorHelper::fullAlpha).orElse(0);
                    color = dye != 0 ? dye : undyed;
                    if (color == 0) continue;
                } else {
                    color = -1;
                }
                out.add(new ArmorLayer(slot, layer.getFullTextureId(type), ColorHelper.fullAlpha(color)));
            }
        }
        return out;
    }

    static void clear() {
        MASKS.clear();
    }
}
