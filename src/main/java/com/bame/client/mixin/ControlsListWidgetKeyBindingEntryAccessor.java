package com.bame.client.mixin;

import net.minecraft.client.gui.screen.option.ControlsListWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ControlsListWidget.KeyBindingEntry.class)
public interface ControlsListWidgetKeyBindingEntryAccessor {
    @Accessor("binding")
    KeyBinding caeser$getBinding();

    @Accessor("bindingName")
    Text caeser$getBindingName();

    /** Vanilla {@code update()}: refreshes button text, Reset button state and the conflict marker. */
    @Invoker("update")
    void caeser$update();
}
