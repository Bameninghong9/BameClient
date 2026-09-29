package com.bame.client.render;

import com.bame.client.module.ScoreboardModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.scoreboard.*;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ScoreboardRenderer {
    public static ScoreboardObjective lastObjective = null;

    private static final Comparator<ScoreboardEntry> ENTRY_ORDERING = Comparator
        .comparing(ScoreboardEntry::value)
        .reversed()
        .thenComparing(ScoreboardEntry::owner, String.CASE_INSENSITIVE_ORDER);

    public record Row(Text name, Text score, int scoreWidth) {}

    public static ScoreboardObjective getCurrentObjective(MinecraftClient client) {
        if (client.world == null || client.player == null) return null;
        Scoreboard scoreboard = client.world.getScoreboard();
        ScoreboardObjective objective = null;
        Team team = scoreboard.getScoreHolderTeam(client.player.getNameForScoreboard());
        if (team != null) {
            ScoreboardDisplaySlot slot = ScoreboardDisplaySlot.fromFormatting(team.getColor());
            if (slot != null) {
                objective = scoreboard.getObjectiveForSlot(slot);
            }
        }
        return objective != null ? objective : scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
    }

    public static int getWidth(MinecraftClient client) {
        if (client == null) client = MinecraftClient.getInstance();
        ScoreboardObjective objective = getCurrentObjective(client);
        if (objective == null) objective = lastObjective;
        if (objective == null) return 110;

        TextRenderer tr = client.textRenderer;
        Scoreboard scoreboard = objective.getScoreboard();
        NumberFormat numberFormat = objective.getNumberFormatOr(StyledNumberFormat.RED);

        int maxW = tr.getWidth(objective.getDisplayName());
        int colonW = tr.getWidth(": ");
        List<ScoreboardEntry> entries = scoreboard.getScoreboardEntries(objective).stream()
            .filter(e -> !e.hidden())
            .sorted(ENTRY_ORDERING)
            .limit(15L)
            .toList();

        for (ScoreboardEntry e : entries) {
            Team team = scoreboard.getScoreHolderTeam(e.owner());
            MutableText name = Team.decorateName(team, e.name());
            MutableText score = e.formatted(numberFormat);
            int scoreW = tr.getWidth(score);
            int w = tr.getWidth(name) + (scoreW > 0 ? scoreW + colonW : 0);
            maxW = Math.max(maxW, w);
        }
        int naturalW = maxW + 14;
        return ScoreboardModule.customWidth > 0 ? Math.max(naturalW, ScoreboardModule.customWidth) : naturalW;
    }

    public static int getHeight(MinecraftClient client) {
        if (client == null) client = MinecraftClient.getInstance();
        ScoreboardObjective objective = getCurrentObjective(client);
        if (objective == null) objective = lastObjective;
        if (objective == null) {
            int naturalH = 6 * 9 + 20;
            return ScoreboardModule.customHeight > 0 ? Math.max(naturalH, ScoreboardModule.customHeight) : naturalH;
        }

        Scoreboard scoreboard = objective.getScoreboard();
        long count = scoreboard.getScoreboardEntries(objective).stream()
            .filter(e -> !e.hidden())
            .limit(15L)
            .count();
        int naturalH = (int) count * 9 + 20;
        return ScoreboardModule.customHeight > 0 ? Math.max(naturalH, ScoreboardModule.customHeight) : naturalH;
    }

    public static void render(DrawContext context, ScoreboardObjective objective, int customX, int customY, float scale, boolean isPreview) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden && !isPreview) return;

        if (objective == null) objective = getCurrentObjective(client);
        if (objective == null) objective = lastObjective;

        if (objective != null) {
            lastObjective = objective;
            renderRealScoreboard(context, client, objective, customX, customY, scale);
        } else if (isPreview) {
            renderPreviewScoreboard(context, client, customX, customY, scale);
        }
    }

    private static void renderRealScoreboard(DrawContext context, MinecraftClient client, ScoreboardObjective objective, int customX, int customY, float scale) {
        TextRenderer tr = client.textRenderer;
        Scoreboard scoreboard = objective.getScoreboard();
        NumberFormat numberFormat = objective.getNumberFormatOr(StyledNumberFormat.RED);

        List<ScoreboardEntry> entries = scoreboard.getScoreboardEntries(objective).stream()
            .filter(e -> !e.hidden())
            .sorted(ENTRY_ORDERING)
            .limit(15L)
            .toList();

        Text title = objective.getDisplayName();
        int maxW = tr.getWidth(title);
        int colonW = tr.getWidth(": ");

        List<Row> rows = new ArrayList<>();
        for (ScoreboardEntry e : entries) {
            Team team = scoreboard.getScoreHolderTeam(e.owner());
            MutableText name = Team.decorateName(team, e.name());
            MutableText score = e.formatted(numberFormat);
            int scoreW = tr.getWidth(score);
            int w = tr.getWidth(name) + (scoreW > 0 ? scoreW + colonW : 0);
            maxW = Math.max(maxW, w);
            rows.add(new Row(name, score, scoreW));
        }

        int naturalW = maxW + 14;
        int naturalH = rows.size() * 9 + 20;
        int w = ScoreboardModule.customWidth > 0 ? Math.max(naturalW, ScoreboardModule.customWidth) : naturalW;
        int h = ScoreboardModule.customHeight > 0 ? Math.max(naturalH, ScoreboardModule.customHeight) : naturalH;

        int defX = client.getWindow().getScaledWidth() - (int)(w * scale) - 3;
        int defY = (client.getWindow().getScaledHeight() - (int)(h * scale)) / 2;
        int drawX = customX == -1 ? defX : customX;
        int drawY = customY == -1 ? defY : customY;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float) drawX, (float) drawY);
        context.getMatrices().scale(scale, scale);

        // Draw custom box background
        StatusHudRenderer.drawBoxBg(context, 0, 0, w, h, ScoreboardModule.bgMode, ScoreboardModule.outlineColor);

        // Draw title
        int titleW = tr.getWidth(title);
        context.drawText(tr, title, (w - titleW) / 2, 4, -1, true);

        // Draw separator line under title if tooltip or blur
        if (ScoreboardModule.bgMode == 1 || ScoreboardModule.bgMode == 2) {
            context.fill(3, 14, w - 3, 15, 0x44FFFFFF);
        }

        // Draw rows
        int curY = 16;
        int rowSpacing = (rows.size() > 1 && h > naturalH) ? Math.max(9, (h - 20) / rows.size()) : 9;
        for (Row row : rows) {
            context.drawText(tr, row.name, 7, curY, -1, true);
            if (row.scoreWidth > 0) {
                context.drawText(tr, row.score, w - row.scoreWidth - 7, curY, -1, true);
            }
            curY += rowSpacing;
        }

        context.getMatrices().popMatrix();
    }

    private static void renderPreviewScoreboard(DrawContext context, MinecraftClient client, int customX, int customY, float scale) {
        TextRenderer tr = client.textRenderer;
        Text title = Text.literal("§6§lSCOREBOARD");

        String[][] dummyRows = {
            {"§eRank: §fPlayer", ""},
            {"§eCoins: §a1,250", ""},
            {"§eKills: §c12", ""},
            {"§eWins: §b4", ""},
            {"§7----------------", ""},
            {"§bplay.caeser.net", ""}
        };

        int maxW = tr.getWidth(title);
        for (String[] r : dummyRows) {
            maxW = Math.max(maxW, tr.getWidth(r[0]));
        }

        int naturalW = maxW + 14;
        int naturalH = dummyRows.length * 9 + 20;
        int w = ScoreboardModule.customWidth > 0 ? Math.max(naturalW, ScoreboardModule.customWidth) : naturalW;
        int h = ScoreboardModule.customHeight > 0 ? Math.max(naturalH, ScoreboardModule.customHeight) : naturalH;

        int defX = client.getWindow().getScaledWidth() - (int)(w * scale) - 3;
        int defY = (client.getWindow().getScaledHeight() - (int)(h * scale)) / 2;
        int drawX = customX == -1 ? defX : customX;
        int drawY = customY == -1 ? defY : customY;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float) drawX, (float) drawY);
        context.getMatrices().scale(scale, scale);

        StatusHudRenderer.drawBoxBg(context, 0, 0, w, h, ScoreboardModule.bgMode, ScoreboardModule.outlineColor);

        int titleW = tr.getWidth(title);
        context.drawText(tr, title, (w - titleW) / 2, 4, -1, true);

        if (ScoreboardModule.bgMode == 1 || ScoreboardModule.bgMode == 2) {
            context.fill(3, 14, w - 3, 15, 0x44FFFFFF);
        }

        int curY = 16;
        int rowSpacing = (dummyRows.length > 1 && h > naturalH) ? Math.max(9, (h - 20) / dummyRows.length) : 9;
        for (String[] r : dummyRows) {
            context.drawText(tr, Text.literal(r[0]), 7, curY, -1, true);
            curY += rowSpacing;
        }

        context.getMatrices().popMatrix();
    }
}
