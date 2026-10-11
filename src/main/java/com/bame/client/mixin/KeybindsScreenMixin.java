package com.bame.client.mixin;

import com.bame.client.gui.search.EntryListSearch;
import com.bame.client.gui.search.KeybindSearch;
import com.bame.client.gui.search.KeybindSearchHost;
import com.bame.client.gui.search.SearchFieldWidget;
import com.bame.client.gui.search.SearchableEntryList;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.ControlsListWidget;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Key Binds search bar: title + search field in an enlarged header, key handling (Ctrl+F, ESC, a waiting key
 * binding always wins) and the "no results" hint.
 */
@Mixin(KeybindsScreen.class)
public abstract class KeybindsScreenMixin extends GameOptionsScreen implements KeybindSearchHost {
    @Shadow public KeyBinding selectedKeyBinding;
    @Shadow private ControlsListWidget controlsList;

    @Unique
    private SearchFieldWidget caeser$searchField;

    protected KeybindsScreenMixin(Screen parent, GameOptions gameOptions, Text title) {
        super(parent, gameOptions, title);
    }

    /** Called from {@link GameOptionsScreenMixin} instead of the vanilla header (title only). */
    @Override
    public void caeser$initSearchHeader() {
        this.layout.setHeaderHeight(KeybindSearch.HEADER_HEIGHT);
        DirectionalLayoutWidget header = DirectionalLayoutWidget.vertical().spacing(KeybindSearch.HEADER_SPACING);
        header.getMainPositioner().alignHorizontalCenter();
        header.add(new TextWidget(this.title, this.textRenderer));

        SearchFieldWidget field = new SearchFieldWidget(EntryListSearch.keybindsPlaceholder());
        // Behind list and footer buttons in Tab order, so keyboard navigation does not focus it when the screen opens
        field.setNavigationOrder(1);
        field.setChangedListener(text -> {
            if (this.controlsList instanceof SearchableEntryList searchable) {
                searchable.caeser$applySearch(text);
            }
        });
        header.add(field);
        this.caeser$searchField = field;
        this.layout.addHeader(header);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void caeser$searchKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        SearchFieldWidget field = this.caeser$searchField;
        if (field == null) return;
        if (this.selectedKeyBinding != null) {
            // A key binding waits for a key: every key (including ESC = unbind) goes to the binding
            EntryListSearch.unfocus(this, field);
            return;
        }
        Boolean result = EntryListSearch.handleKey(this, field, input);
        if (result != null) cir.setReturnValue(result);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void caeser$renderNoResults(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        EntryListSearch.drawNoResultsIfEmpty(context, this.controlsList, this.caeser$searchField, EntryListSearch.keybindsNoResults());
    }
}
