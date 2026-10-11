package com.bame.client.mixin;

import com.bame.client.gui.search.EntryListSearch;
import com.bame.client.gui.search.SearchFieldWidget;
import com.bame.client.gui.search.StatsSearch;
import com.bame.client.gui.search.StatsTabAccess;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.StatsScreen;
import net.minecraft.client.gui.tab.Tab;
import net.minecraft.client.gui.tab.TabManager;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.widget.TabNavigationWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Statistics search bar: field centred below the tab bar (hidden while "Downloading statistics..." is shown),
 * filters General / Items / Mobs by the displayed name. The query is kept across tab switches.
 */
@Mixin(StatsScreen.class)
public abstract class StatsScreenMixin extends Screen {
    @Shadow private TabNavigationWidget tabNavigationWidget;
    @Shadow private boolean downloadingStats;
    @Shadow @Final private TabManager tabManager;

    @Unique private SearchFieldWidget caeser$searchField;
    @Unique private String caeser$query = "";
    @Unique private final StatsSearch.Snapshots caeser$snapshots = new StatsSearch.Snapshots();

    protected StatsScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void caeser$addSearchField(CallbackInfo ci) {
        SearchFieldWidget field = new SearchFieldWidget(EntryListSearch.statsPlaceholder());
        field.setNavigationOrder(1);
        if (!this.caeser$query.isEmpty()) field.setText(this.caeser$query);
        field.setChangedListener(text -> {
            this.caeser$query = text;
            this.caeser$snapshots.applyAll(text);
        });
        this.caeser$searchField = field;
        this.addDrawableChild(field);
        this.caeser$updateSearchField();
    }

    /** Stats arrived from the server: the lists were (re)built, remember them and apply the current query. */
    @Inject(method = "onStatsReady", at = @At("TAIL"))
    private void caeser$onStatsReady(CallbackInfo ci) {
        if (this.caeser$snapshots.update(this.caeser$currentLists())) {
            this.caeser$snapshots.applyAll(this.caeser$query);
        }
        this.caeser$updateSearchField();
    }

    @Inject(method = "refreshWidgetPositions", at = @At("TAIL"))
    private void caeser$positionSearchField(CallbackInfo ci) {
        this.caeser$updateSearchField();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void caeser$searchKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        Boolean result = EntryListSearch.handleKey(this, this.caeser$searchField, input);
        if (result != null) cir.setReturnValue(result);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void caeser$renderNoResults(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (this.downloadingStats) return;
        Tab tab = this.tabManager.getCurrentTab();
        if (tab instanceof StatsTabAccess statsTab && this.caeser$snapshots.hasEntries(statsTab.caeser$getList())) {
            EntryListSearch.drawNoResultsIfEmpty(context, statsTab.caeser$getList(), this.caeser$searchField, EntryListSearch.statsNoResults());
        }
    }

    @Unique
    private List<EntryListWidget<?>> caeser$currentLists() {
        List<EntryListWidget<?>> lists = new ArrayList<>();
        if (this.tabNavigationWidget != null) {
            for (Tab tab : this.tabNavigationWidget.getTabs()) {
                if (tab instanceof StatsTabAccess statsTab && statsTab.caeser$getList() != null) {
                    lists.add(statsTab.caeser$getList());
                }
            }
        }
        return lists;
    }

    /** Centred directly below the tab bar; hidden and disabled while the statistics are still downloading. */
    @Unique
    private void caeser$updateSearchField() {
        SearchFieldWidget field = this.caeser$searchField;
        if (field == null) return;
        boolean ready = !this.downloadingStats && this.tabNavigationWidget != null;
        field.visible = ready;
        field.active = ready;
        if (!ready) EntryListSearch.unfocus(this, field);
        int tabBarBottom = this.tabNavigationWidget != null ? this.tabNavigationWidget.getNavigationFocus().getBottom() : 24;
        field.setPosition(this.width / 2 - field.getWidth() / 2, tabBarBottom + StatsSearch.FIELD_GAP);
    }
}
