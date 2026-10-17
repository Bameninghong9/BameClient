package com.bame.client.gui;

import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Random;

/**
 * Prüft ohne Minecraft die Rechnung des Kauf-Buttons auf den Cosmetics-Karten ({@link BuyButtonLayout}):
 * Preisformatierung (Tausendertrennzeichen je Spielsprache, Kompaktform), Platzierung (immer in der Karte, mittig,
 * nie über Name/Schloss, schrittweise kompakter bei schmalen Karten), Treffer-Test Button vs. Karte, „Test“-Knopf der
 * Kill-Effekt-Karten und die verkleinerte Vorschau. Gradle-Aufgabe {@code buyButtonTest} (läuft mit {@code check}).
 */
public final class BuyButtonTest {
    private static int checks = 0;

    /** Höhe der Karten im Menü (CosmeticsTab.CARD_HEIGHT). */
    private static final int CARD_H = 112;

    public static void main(String[] args) {
        groupedFormat();
        compactFormat();
        separators();
        placementInsideCard();
        placementVariants();
        placementRealMenuSizes();
        hitTest();
        killTestButton();
        previewArea();
        System.out.println("BUY BUTTON TESTS PASSED (" + checks + " checks)");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    private static void eq(Object actual, Object expected, String what) {
        check(expected.equals(actual), what + ": expected <" + expected + "> but was <" + actual + ">");
    }

    // ------------------------------------------------------------------------------------------------------------
    // Preis-Text
    // ------------------------------------------------------------------------------------------------------------

    private static void groupedFormat() {
        eq(BuyButtonLayout.formatGrouped(0, '.'), "0", "zero");
        eq(BuyButtonLayout.formatGrouped(7, '.'), "7", "one digit");
        eq(BuyButtonLayout.formatGrouped(250, '.'), "250", "no separator below 1000");
        eq(BuyButtonLayout.formatGrouped(999, ','), "999", "999");
        eq(BuyButtonLayout.formatGrouped(1000, '.'), "1.000", "1000 de");
        eq(BuyButtonLayout.formatGrouped(1500, '.'), "1.500", "1500 de");
        eq(BuyButtonLayout.formatGrouped(1500, ','), "1,500", "1500 en");
        eq(BuyButtonLayout.formatGrouped(1500, ' '), "1 500", "1500 fr");
        eq(BuyButtonLayout.formatGrouped(1500, '\''), "1'500", "1500 de-CH");
        eq(BuyButtonLayout.formatGrouped(12500, '.'), "12.500", "12500");
        eq(BuyButtonLayout.formatGrouped(100000, '.'), "100.000", "100000");
        eq(BuyButtonLayout.formatGrouped(1234567, ','), "1,234,567", "1234567");
        eq(BuyButtonLayout.formatGrouped(Integer.MAX_VALUE, '.'), "2.147.483.647", "int max");
        eq(BuyButtonLayout.formatGrouped(-1500, '.'), "-1.500", "negative");
        eq(BuyButtonLayout.formatGrouped(-999, '.'), "-999", "negative below 1000");
        eq(BuyButtonLayout.formatGrouped(Integer.MIN_VALUE, '.'), "-2.147.483.648", "int min");
        Random random = new Random(11);
        for (int i = 0; i < 20000; i++) {
            int v = i < 5000 ? i * 37 : random.nextInt();
            String s = BuyButtonLayout.formatGrouped(v, '.');
            check(s.replace(".", "").equals(Integer.toString(v)), "grouping keeps the digits " + v + " -> " + s);
            String digits = s.startsWith("-") ? s.substring(1) : s;
            String[] groups = digits.split("\\.");
            check(groups[0].length() >= 1 && groups[0].length() <= 3, "first group 1-3 digits " + s);
            for (int g = 1; g < groups.length; g++) check(groups[g].length() == 3, "groups of three " + s);
        }
    }

    private static void compactFormat() {
        eq(BuyButtonLayout.formatCompact(0, ','), "0", "zero");
        eq(BuyButtonLayout.formatCompact(250, ','), "250", "below 1000 unchanged");
        eq(BuyButtonLayout.formatCompact(999, ','), "999", "999");
        eq(BuyButtonLayout.formatCompact(1000, ','), "1k", "1000");
        eq(BuyButtonLayout.formatCompact(1050, ','), "1k", "1050 cut, no trailing ,0");
        eq(BuyButtonLayout.formatCompact(1500, ','), "1,5k", "1500 de");
        eq(BuyButtonLayout.formatCompact(1500, '.'), "1.5k", "1500 en");
        eq(BuyButtonLayout.formatCompact(1599, ','), "1,5k", "cut instead of rounding");
        eq(BuyButtonLayout.formatCompact(9999, ','), "9,9k", "9999 stays below 10k");
        eq(BuyButtonLayout.formatCompact(10000, ','), "10k", "10000");
        eq(BuyButtonLayout.formatCompact(12500, ','), "12k", "no decimal from 10k");
        eq(BuyButtonLayout.formatCompact(999999, ','), "999k", "999999");
        eq(BuyButtonLayout.formatCompact(1000000, ','), "1M", "1M");
        eq(BuyButtonLayout.formatCompact(1500000, ','), "1,5M", "1,5M");
        eq(BuyButtonLayout.formatCompact(25000000, '.'), "25M", "25M");
        eq(BuyButtonLayout.formatCompact(Integer.MAX_VALUE, ','), "2,1B", "int max");
        eq(BuyButtonLayout.formatCompact(-1500, ','), "-1,5k", "negative");
        eq(BuyButtonLayout.formatCompact(Integer.MIN_VALUE, '.'), "-2.1B", "int min");
        Random random = new Random(5);
        for (int i = 0; i < 20000; i++) {
            int v = i < 4000 ? i * 13 : random.nextInt(Integer.MAX_VALUE);
            String full = BuyButtonLayout.formatGrouped(v, '.');
            String compact = BuyButtonLayout.formatCompact(v, ',');
            check(compact.length() <= full.length(), "compact never longer: " + v + " " + compact + " vs " + full);
            if (v < 1000) eq(compact, full, "below 1000 compact == full");
            check(compact.length() <= 5, "compact at most 5 characters: " + compact);
            check(compactValue(compact) <= v, "compact never shows more than the price: " + v + " -> " + compact);
            check(compactValue(compact) * 1.11 + 1 >= v || v < 1000, "compact close to the price: " + v + " -> " + compact);
        }
    }

    /** Liest „1,5k“ wieder als Zahl (für den Vergleich mit dem echten Preis). */
    private static double compactValue(String s) {
        double mult = 1;
        char last = s.charAt(s.length() - 1);
        if (last == 'k') mult = 1e3;
        else if (last == 'M') mult = 1e6;
        else if (last == 'B') mult = 1e9;
        String num = mult == 1 ? s : s.substring(0, s.length() - 1);
        return Double.parseDouble(num.replace(',', '.')) * mult;
    }

    private static void separators() {
        // ohne Gebietsschema-Daten der Laufzeit: feste Tabelle
        eq(BuyButtonLayout.fallbackSeparators("de"), BuyButtonLayout.DOT_COMMA, "fallback de");
        eq(BuyButtonLayout.fallbackSeparators("es"), BuyButtonLayout.DOT_COMMA, "fallback es");
        eq(BuyButtonLayout.fallbackSeparators("fr"), BuyButtonLayout.SPACE_COMMA, "fallback fr");
        eq(BuyButtonLayout.fallbackSeparators("ru"), BuyButtonLayout.SPACE_COMMA, "fallback ru");
        eq(BuyButtonLayout.fallbackSeparators("en"), BuyButtonLayout.ENGLISH, "fallback en");
        eq(BuyButtonLayout.fallbackSeparators("ja"), BuyButtonLayout.ENGLISH, "fallback ja");
        eq(BuyButtonLayout.fallbackSeparators("zz"), BuyButtonLayout.ENGLISH, "fallback unknown");
        eq(BuyButtonLayout.fallbackSeparators(null), BuyButtonLayout.ENGLISH, "fallback null");

        // Schriftsichere Zeichen
        eq(BuyButtonLayout.normalize('.', ','), BuyButtonLayout.DOT_COMMA, "normalize de");
        eq(BuyButtonLayout.normalize((char) 0x202F, ','), BuyButtonLayout.SPACE_COMMA, "narrow nbsp -> space");
        eq(BuyButtonLayout.normalize((char) 0x00A0, ','), BuyButtonLayout.SPACE_COMMA, "nbsp -> space");
        eq(BuyButtonLayout.normalize((char) 0x2019, '.'), new BuyButtonLayout.Separators('\'', '.'), "swiss quote -> apostrophe");
        check(BuyButtonLayout.normalize((char) 0x066C, (char) 0x066B) == null, "arabic separators are not drawable");
        check(BuyButtonLayout.normalize(',', ',') == null, "same character for both is rejected");
        check(BuyButtonLayout.normalize('.', 'x') == null, "unknown decimal separator rejected");

        check(BuyButtonLayout.localeOf(null) == null && BuyButtonLayout.localeOf("") == null
                && BuyButtonLayout.localeOf("  ") == null && BuyButtonLayout.localeOf("_us") == null, "empty codes");
        eq(BuyButtonLayout.localeOf("de_de"), Locale.of("de", "DE"), "locale de_de");
        eq(BuyButtonLayout.localeOf("EN_us"), Locale.of("en", "US"), "locale case-insensitive");
        eq(BuyButtonLayout.localeOf("fr"), Locale.of("fr"), "locale without country");

        // Spielsprache → Trennzeichen (wie im Auftrag: „1.500“ bzw. „1,500“)
        eq(BuyButtonLayout.separatorsFor("de_de"), BuyButtonLayout.DOT_COMMA, "de_de");
        eq(BuyButtonLayout.separatorsFor("de_at").decimal(), ',', "de_at decimal comma");
        eq(BuyButtonLayout.separatorsFor("en_us"), BuyButtonLayout.ENGLISH, "en_us");
        eq(BuyButtonLayout.separatorsFor("en_gb"), BuyButtonLayout.ENGLISH, "en_gb");
        eq(BuyButtonLayout.separatorsFor("fr_fr"), BuyButtonLayout.SPACE_COMMA, "fr_fr");
        eq(BuyButtonLayout.separatorsFor("ru_ru"), BuyButtonLayout.SPACE_COMMA, "ru_ru");
        eq(BuyButtonLayout.separatorsFor("es_es"), BuyButtonLayout.DOT_COMMA, "es_es");
        eq(BuyButtonLayout.separatorsFor("pt_br"), BuyButtonLayout.DOT_COMMA, "pt_br");
        eq(BuyButtonLayout.separatorsFor("ja_jp"), BuyButtonLayout.ENGLISH, "ja_jp");
        eq(BuyButtonLayout.separatorsFor("ar_sa"), BuyButtonLayout.ENGLISH, "ar_sa: own digits -> english");
        eq(BuyButtonLayout.separatorsFor(null), BuyButtonLayout.ENGLISH, "no language");
        eq(BuyButtonLayout.separatorsFor(""), BuyButtonLayout.ENGLISH, "empty language");
        eq(BuyButtonLayout.formatGrouped(1500, BuyButtonLayout.separatorsFor("de_de").grouping()), "1.500", "German price");
        eq(BuyButtonLayout.formatGrouped(1500, BuyButtonLayout.separatorsFor("en_us").grouping()), "1,500", "English price");
        eq(BuyButtonLayout.formatCompact(1500, BuyButtonLayout.separatorsFor("de_de").decimal()), "1,5k", "German compact");
        eq(BuyButtonLayout.formatCompact(1500, BuyButtonLayout.separatorsFor("en_us").decimal()), "1.5k", "English compact");
        boolean jdkData = DecimalFormatSymbols.getInstance(Locale.GERMANY).getDecimalSeparator() == ',';
        if (jdkData) {
            eq(BuyButtonLayout.separatorsFor("de_ch"), new BuyButtonLayout.Separators('\'', '.'), "de_ch");
            eq(BuyButtonLayout.separatorsFor("es_mx"), BuyButtonLayout.ENGLISH, "es_mx");
        }

        // Alle Minecraft-Sprachen: nie ein Fehler, nur schriftsichere Zeichen, Tausender ≠ Dezimal
        String[] codes = {"af_za", "ar_sa", "ast_es", "az_az", "ba_ru", "bar", "be_by", "be_latn", "bg_bg", "br_fr",
                "brb", "bs_ba", "ca_es", "cs_cz", "cy_gb", "da_dk", "de_at", "de_ch", "de_de", "el_gr", "en_au", "en_ca",
                "en_gb", "en_nz", "en_pt", "en_ud", "en_us", "enp", "enws", "eo_uy", "es_ar", "es_cl", "es_ec", "es_es",
                "es_mx", "es_uy", "es_ve", "esan", "et_ee", "eu_es", "fa_ir", "fi_fi", "fil_ph", "fo_fo", "fr_ca",
                "fr_fr", "fra_de", "fur_it", "fy_nl", "ga_ie", "gd_gb", "gl_es", "haw_us", "he_il", "hi_in", "hn_no",
                "hr_hr", "hu_hu", "hy_am", "id_id", "ig_ng", "io_en", "is_is", "isv", "it_it", "ja_jp", "jbo_en",
                "ka_ge", "kk_kz", "kn_in", "ko_kr", "ksh", "kw_gb", "ky_kg", "la_la", "lb_lu", "li_li", "lmo", "lo_la",
                "lol_us", "lt_lt", "lv_lv", "lzh", "mk_mk", "mn_mn", "ms_my", "mt_mt", "nah", "nds_de", "nl_be",
                "nl_nl", "nn_no", "no_no", "oc_fr", "ovd", "pl_pl", "pls", "pt_br", "pt_pt", "qya_aa", "ro_ro", "rpr",
                "ru_ru", "ry_ua", "sah_sah", "se_no", "sk_sk", "sl_si", "so_so", "sq_al", "sr_cs", "sr_sp", "sv_se",
                "sxu", "szl", "ta_in", "th_th", "tl_ph", "tlh_aa", "tok", "tr_tr", "tt_ru", "tzo_mx", "uk_ua", "val_es",
                "vec_it", "vi_vn", "vp_vl", "yi_de", "yo_ng", "zh_cn", "zh_hk", "zh_tw", "zlm_arab", "DE-DE", " de_de "};
        for (String code : codes) {
            BuyButtonLayout.Separators s = BuyButtonLayout.separatorsFor(code);
            check(".,' ".indexOf(s.grouping()) >= 0, "drawable grouping for " + code + ": " + (int) s.grouping());
            check(s.decimal() == '.' || s.decimal() == ',', "drawable decimal for " + code);
            check(s.grouping() != s.decimal(), "grouping differs from decimal for " + code);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Platzierung
    // ------------------------------------------------------------------------------------------------------------

    /** Grobe Breiten der Menü-Schrift (sans, Größe 10) für realistische Preis-Texte. */
    private static int textWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            w += switch (ch) {
                case '.', ',', '\'' -> 2;
                case ' ' -> 3;
                case '1' -> 4;
                case 'M' -> 8;
                case 'k', 'B' -> 5;
                default -> 6;
            };
        }
        return w;
    }

    private static final int[] PRICES = {1, 9, 50, 99, 250, 500, 999, 1000, 1500, 2750, 9999, 12500, 50000, 100000,
            999999, 1234567, Integer.MAX_VALUE};
    private static final char[][] SEPARATORS = {{'.', ','}, {',', '.'}, {' ', ','}, {'\'', '.'}};

    /** Prüft alle Eigenschaften einer Platzierung; gibt sie zurück. */
    private static BuyButtonLayout.Placement checkedPlace(int cardW, int cardH, int fullW, int compactW, String ctx) {
        BuyButtonLayout.Placement p = BuyButtonLayout.place(cardW, cardH, fullW, compactW);
        if (p == null) {
            int minW = Math.max(BuyButtonLayout.HEIGHT, 2 * BuyButtonLayout.PAD_MIN
                    + (compactW > 0 && compactW < fullW ? compactW : fullW));
            boolean tooLow = cardH - BuyButtonLayout.BOTTOM_MARGIN - BuyButtonLayout.HEIGHT - BuyButtonLayout.PREVIEW_GAP
                    - BuyButtonLayout.TOP_RESERVED < BuyButtonLayout.MIN_PREVIEW_HEIGHT;
            check(tooLow || fullW <= 0 || minW > cardW - 2 * BuyButtonLayout.SIDE_MARGIN,
                    "no button although the text-only pill fits " + ctx);
            return null;
        }
        int glow = BuyButtonLayout.GLOW;
        // innerhalb der Karte, auch mit Leuchten (Kartenrand-Pixel bleibt frei)
        check(p.x() >= BuyButtonLayout.SIDE_MARGIN && p.right() <= cardW - BuyButtonLayout.SIDE_MARGIN, "inside horizontally " + ctx);
        check(p.x() - glow >= 1 && p.right() + glow <= cardW - 1, "glow inside horizontally " + ctx);
        check(p.bottom() == cardH - BuyButtonLayout.BOTTOM_MARGIN && p.bottom() + glow <= cardH - 1, "bottom margin " + ctx);
        check(p.h() == BuyButtonLayout.HEIGHT && p.w() >= p.h(), "pill shape " + ctx);
        // Kopfzeile frei: Name (y 6..15), Schloss (y 5..18), Stift (y 5..18)
        check(p.y() - glow >= BuyButtonLayout.TOP_RESERVED, "below name and lock " + ctx);
        check(!overlaps(p.x() - glow, p.y() - glow, p.w() + 2 * glow, p.h() + 2 * glow, 6, 6, cardW, 9), "name free " + ctx);
        check(!overlaps(p.x() - glow, p.y() - glow, p.w() + 2 * glow, p.h() + 2 * glow, cardW - 18, 5, 13, 13), "lock free " + ctx);
        check(!overlaps(p.x() - glow, p.y() - glow, p.w() + 2 * glow, p.h() + 2 * glow, cardW - 35, 5, 13, 13), "pencil free " + ctx);
        // mittig
        check(Math.abs((p.x() + p.w() / 2.0) - cardW / 2.0) <= 0.5, "centered " + ctx);
        // Inhalt in der Pille, Jeton links neben dem Preis
        check(p.textY() == p.y() + BuyButtonLayout.TEXT_DY && p.textY() + 8 <= p.bottom(), "text vertically inside " + ctx);
        int minPad = p.coinSize() > 0 ? BuyButtonLayout.PAD_TIGHT : BuyButtonLayout.PAD_MIN;
        check(p.textX() >= p.x() + minPad && p.textX() + p.textW() <= p.right() - minPad, "text horizontally inside " + ctx);
        int expectedText = p.compactLabel() ? compactW : fullW;
        check(p.textW() == expectedText, "text width matches the chosen label " + ctx);
        if (p.coinSize() > 0) {
            check(p.coinX() >= p.x() + BuyButtonLayout.PAD_TIGHT && p.coinX() + p.coinSize() < p.textX(), "coin left of the price " + ctx);
            check(p.coinY() >= p.y() + 1 && p.coinY() + p.coinSize() <= p.bottom() - 1, "coin vertically inside " + ctx);
        } else {
            check(p.variant() == BuyButtonLayout.VARIANT_TEXT_ONLY, "only the text-only variant has no coin " + ctx);
        }
        check(p.compactLabel() == (p.variant() == BuyButtonLayout.VARIANT_COMPACT
                || p.variant() == BuyButtonLayout.VARIANT_COMPACT_TIGHT
                || (p.variant() == BuyButtonLayout.VARIANT_TEXT_ONLY && compactW > 0 && compactW < fullW)), "label choice " + ctx);
        return p;
    }

    private static boolean overlaps(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) {
        return ax < bx + bw && bx < ax + aw && ay < by + bh && by < ay + ah;
    }

    private static void placementInsideCard() {
        int[] heights = {0, 20, 40, 61, 62, 63, 80, 100, CARD_H, 140, 200};
        for (int price : PRICES) {
            for (char[] sep : SEPARATORS) {
                String full = BuyButtonLayout.formatGrouped(price, sep[0]);
                String compact = BuyButtonLayout.formatCompact(price, sep[1]);
                int fullW = textWidth(full);
                int compactW = textWidth(compact);
                for (int h : heights) {
                    int lastVariant = Integer.MAX_VALUE;
                    boolean seen = false;
                    for (int w = 0; w <= 420; w++) {
                        String ctx = "(card " + w + "x" + h + ", " + full + " / " + compact + ")";
                        BuyButtonLayout.Placement p = checkedPlace(w, h, fullW, compactW, ctx);
                        if (p == null) {
                            check(!seen, "a wider card never loses the button " + ctx);
                            continue;
                        }
                        seen = true;
                        check(p.variant() <= lastVariant, "a wider card never gets a narrower variant " + ctx);
                        lastVariant = p.variant();
                    }
                }
            }
        }
        // zufällige Breiten (auch unrealistische)
        Random random = new Random(42);
        for (int i = 0; i < 200000; i++) {
            int w = random.nextInt(500);
            int h = random.nextInt(260);
            int fullW = 1 + random.nextInt(140);
            int compactW = random.nextInt(4) == 0 ? 0 : 1 + random.nextInt(140);
            checkedPlace(w, h, fullW, compactW, "(random " + w + "x" + h + " " + fullW + "/" + compactW + ")");
        }
        check(BuyButtonLayout.place(100, CARD_H, 0, 0) == null, "no label -> no button");
        check(BuyButtonLayout.place(-5, CARD_H, 20, 10) == null, "negative width -> no button");
    }

    private static void placementVariants() {
        int fullW = textWidth("1.500");   // 4 + 2 + 18 = 24
        int compactW = textWidth("1,5k"); // 4 + 2 + 6 + 5 = 17
        int maxFor = BuyButtonLayout.SIDE_MARGIN * 2;
        int fullNormal = BuyButtonLayout.PAD * 2 + BuyButtonLayout.COIN + BuyButtonLayout.GAP + fullW;
        int fullTight = BuyButtonLayout.PAD_TIGHT * 2 + BuyButtonLayout.COIN_TIGHT + BuyButtonLayout.GAP_TIGHT + fullW;
        int compactNormal = BuyButtonLayout.PAD * 2 + BuyButtonLayout.COIN + BuyButtonLayout.GAP + compactW;
        int compactTight = BuyButtonLayout.PAD_TIGHT * 2 + BuyButtonLayout.COIN_TIGHT + BuyButtonLayout.GAP_TIGHT + compactW;
        int textOnly = BuyButtonLayout.PAD_MIN * 2 + compactW;

        BuyButtonLayout.Placement p = BuyButtonLayout.place(fullNormal + maxFor, CARD_H, fullW, compactW);
        eq(p.variant(), BuyButtonLayout.VARIANT_FULL, "exactly wide enough for the full pill");
        eq(p.w(), fullNormal, "full pill width adapts to the price");
        eq(p.coinSize(), BuyButtonLayout.COIN, "normal coin");
        p = BuyButtonLayout.place(fullNormal + maxFor - 1, CARD_H, fullW, compactW);
        eq(p.variant(), BuyButtonLayout.VARIANT_FULL_TIGHT, "one pixel less: tighter padding first");
        eq(p.coinSize(), BuyButtonLayout.COIN_TIGHT, "smaller coin when tight");
        p = BuyButtonLayout.place(fullTight + maxFor - 1, CARD_H, fullW, compactW);
        check(p.variant() == BuyButtonLayout.VARIANT_COMPACT || p.variant() == BuyButtonLayout.VARIANT_COMPACT_TIGHT,
                "then the compact price");
        check(p.compactLabel(), "compact label chosen");
        eq(BuyButtonLayout.place(compactNormal + maxFor, CARD_H, 60, compactW).variant(), BuyButtonLayout.VARIANT_COMPACT,
                "long full price: compact price with normal padding");
        p = BuyButtonLayout.place(compactTight + maxFor - 1, CARD_H, fullW, compactW);
        eq(p.variant(), BuyButtonLayout.VARIANT_TEXT_ONLY, "last resort: price without coin");
        eq(p.coinSize(), 0, "no coin in the last resort");
        check(BuyButtonLayout.place(textOnly + maxFor - 1, CARD_H, fullW, compactW) == null, "nothing fits -> no button");

        // Preise unter 1000 haben keine kürzere Form: direkt ohne Jeton statt „kompakt“
        int w250 = textWidth("250");
        BuyButtonLayout.Placement small = BuyButtonLayout.place(BuyButtonLayout.PAD_MIN * 2 + w250 + maxFor, CARD_H, w250, w250);
        eq(small.variant(), BuyButtonLayout.VARIANT_TEXT_ONLY, "250 on a tiny card");
        check(!small.compactLabel(), "250 has no compact label");
        // schmalste Menü-Karte (320 px Fensterbreite, 32 px): „1,5k“ ohne Jeton passt noch
        BuyButtonLayout.Placement tiny = BuyButtonLayout.place(32, CARD_H, 24, 18);
        check(tiny != null && tiny.variant() == BuyButtonLayout.VARIANT_TEXT_ONLY && tiny.compactLabel(), "32 px card: 1,5k");
    }

    /** Kartenbreite wie im Menü (BameClientScreen.geometry + CosmeticsTab.render) für eine skalierte Fensterbreite. */
    private static int menuCardWidth(int screenW) {
        int pw = Math.min(screenW - 32, Math.max(760, (int) (screenW * 0.86)));
        int cw = pw - 112 - 24;
        int columnWidth = (cw - 16) / 2;
        int totalW = columnWidth * 2 + 16;
        boolean showPreview = totalW >= 380;
        int previewW = showPreview ? Math.clamp((int) (totalW * 0.35f), 160, 210) : 0;
        int cardsAreaW = showPreview ? (totalW - previewW - 14) : totalW;
        return (cardsAreaW - 3 * 8) / 4;
    }

    private static void placementRealMenuSizes() {
        // 1920x1080 bei GUI-Skalierung 2: volle Pille mit „1.500“
        int w1080 = menuCardWidth(960);
        eq(w1080, 110, "card width 1920x1080 @2");
        BuyButtonLayout.Placement p = checkedPlace(w1080, CARD_H, textWidth("1.500"), textWidth("1,5k"), "1080p@2");
        eq(p.variant(), BuyButtonLayout.VARIANT_FULL, "1080p@2 full pill");
        check(!p.compactLabel(), "1080p@2 full price");
        int minSeen = Integer.MAX_VALUE;
        for (int screenW = 320; screenW <= 3840; screenW++) {
            int cardW = menuCardWidth(screenW);
            minSeen = Math.min(minSeen, cardW);
            for (int price : PRICES) {
                for (char[] sep : SEPARATORS) {
                    String full = BuyButtonLayout.formatGrouped(price, sep[0]);
                    String compact = BuyButtonLayout.formatCompact(price, sep[1]);
                    BuyButtonLayout.Placement q = checkedPlace(cardW, CARD_H, textWidth(full), textWidth(compact),
                            "(screen " + screenW + ", card " + cardW + ", " + full + ")");
                    // realistische Preise bis 99.999 haben ab 40 px breiten Karten immer einen Button
                    if (price <= 99_999 && cardW >= 40) check(q != null, "button on a " + cardW + " px card for " + full);
                }
            }
        }
        check(minSeen <= 32, "smallest menu cards were covered (" + minSeen + " px)");
    }

    // ------------------------------------------------------------------------------------------------------------
    // Treffer-Test
    // ------------------------------------------------------------------------------------------------------------

    private static void hitTest() {
        Random random = new Random(7);
        int[] widths = {32, 45, 58, 67, 72, 88, 110, 138, 200, 316};
        for (int w : widths) {
            for (int price : PRICES) {
                String full = BuyButtonLayout.formatGrouped(price, '.');
                String compact = BuyButtonLayout.formatCompact(price, ',');
                BuyButtonLayout.Placement p = BuyButtonLayout.place(w, CARD_H, textWidth(full), textWidth(compact));
                int buttonClicks = 0;
                int cardClicks = 0;
                for (int i = 0; i < 4000; i++) {
                    double x = -10 + random.nextDouble() * (w + 20);
                    double y = -10 + random.nextDouble() * (CARD_H + 20);
                    int hit = BuyButtonLayout.hitTarget(p, x, y, w, CARD_H);
                    boolean inCard = x >= 0 && y >= 0 && x < w && y < CARD_H;
                    boolean inButton = p != null && x >= p.x() && x < p.right() && y >= p.y() && y < p.bottom();
                    if (!inCard) {
                        eq(hit, BuyButtonLayout.HIT_NONE, "outside the card " + x + "," + y);
                        continue;
                    }
                    eq(hit, inButton ? BuyButtonLayout.HIT_BUTTON : BuyButtonLayout.HIT_CARD, "card " + w + " point " + x + "," + y);
                    // Klick-Weiterleitung wie im Menü: entweder Button oder Karte, nie beides
                    if (hit == BuyButtonLayout.HIT_BUTTON) buttonClicks++;
                    else cardClicks++;
                    if (inButton) check(y >= BuyButtonLayout.TOP_RESERVED, "button never under the header");
                }
                if (p != null) check(buttonClicks > 0 && cardClicks > 0, "both targets reachable on a " + w + " px card");
                if (p != null) {
                    // Ränder: links/oben gehören zum Button, rechts/unten (exklusiv) zur Karte
                    eq(BuyButtonLayout.hitTarget(p, p.x(), p.y(), w, CARD_H), BuyButtonLayout.HIT_BUTTON, "top-left corner");
                    eq(BuyButtonLayout.hitTarget(p, p.right() - 0.01, p.bottom() - 0.01, w, CARD_H), BuyButtonLayout.HIT_BUTTON, "inner bottom-right");
                    eq(BuyButtonLayout.hitTarget(p, p.right(), p.y() + 3, w, CARD_H), BuyButtonLayout.HIT_CARD, "right edge exclusive");
                    eq(BuyButtonLayout.hitTarget(p, p.x() + 3, p.bottom(), w, CARD_H), BuyButtonLayout.HIT_CARD, "bottom edge exclusive");
                    eq(BuyButtonLayout.hitTarget(p, p.x() - 0.01, p.y() + 3, w, CARD_H), BuyButtonLayout.HIT_CARD, "left of the pill");
                    eq(BuyButtonLayout.hitTarget(p, p.x() + 3, p.y() - 0.01, w, CARD_H), BuyButtonLayout.HIT_CARD, "above the pill (preview)");
                    eq(BuyButtonLayout.hitTarget(p, w / 2.0, 40, w, CARD_H), BuyButtonLayout.HIT_CARD, "preview area is the card");
                    eq(BuyButtonLayout.hitTarget(p, 8, 8, w, CARD_H), BuyButtonLayout.HIT_CARD, "name is the card");
                }
            }
        }
        eq(BuyButtonLayout.hitTarget(null, 10, 100, 110, CARD_H), BuyButtonLayout.HIT_CARD, "no button: whole card");
        eq(BuyButtonLayout.hitTarget(null, 110, 10, 110, CARD_H), BuyButtonLayout.HIT_NONE, "right card edge exclusive");
    }

    // ------------------------------------------------------------------------------------------------------------
    // Kill-Effekt-Karte: „Test“-Knopf neben bzw. über der Pille
    // ------------------------------------------------------------------------------------------------------------

    private static void killTestButton() {
        int testW = textWidth("Test") + 10;
        int testH = 13;
        int equippedW = textWidth("EQUIPPED") + 10;
        for (int w = 20; w <= 420; w++) {
            // ohne Pille: genau wie bisher
            int base = CARD_H - testH - 4;
            eq(BuyButtonLayout.testButtonY(w, CARD_H, testW, testH, null, 0), base, "no pill, nothing left (" + w + ")");
            boolean compact = 5 + equippedW + 6 + testW + 5 > w;
            eq(BuyButtonLayout.testButtonY(w, CARD_H, testW, testH, null, equippedW), compact ? base - 15 : base,
                    "no pill, EQUIPPED left (" + w + ")");

            for (int price : PRICES) {
                String full = BuyButtonLayout.formatGrouped(price, '.');
                String compactLabel = BuyButtonLayout.formatCompact(price, ',');
                BuyButtonLayout.Placement p = BuyButtonLayout.place(w, CARD_H, textWidth(full), textWidth(compactLabel));
                if (p == null) continue;
                int ty = BuyButtonLayout.testButtonY(w, CARD_H, testW, testH, p, 0);
                int tx = w - testW - BuyButtonLayout.TEST_RIGHT;
                String ctx = "(card " + w + ", " + full + ")";
                check(tx >= 0 || w < testW + 5, "test button inside horizontally " + ctx);
                check(ty >= BuyButtonLayout.TOP_RESERVED && ty + testH <= CARD_H - 4, "test button inside vertically " + ctx);
                int g = BuyButtonLayout.GLOW;
                check(!overlaps(tx, ty, testW, testH, p.x() - g, p.y() - g, p.w() + 2 * g, p.h() + 2 * g),
                        "test button never under the pill or its glow " + ctx);
                if (ty == base) {
                    check(p.right() + g < tx, "same row only with a gap " + ctx);
                } else {
                    eq(ty + testH + BuyButtonLayout.PREVIEW_GAP, p.y(), "moved up directly above the pill " + ctx);
                }
            }
        }
        // 1080p@2: Pille mit „1.500“ und „Test“ passen nicht nebeneinander -> Test eine Zeile höher
        BuyButtonLayout.Placement p = BuyButtonLayout.place(110, CARD_H, textWidth("1.500"), textWidth("1,5k"));
        check(BuyButtonLayout.testButtonY(110, CARD_H, testW, testH, p, 0) < CARD_H - testH - 4, "110 px: test moves up");
        // breite Karte: alles in einer Zeile
        p = BuyButtonLayout.place(316, CARD_H, textWidth("1.500"), textWidth("1,5k"));
        eq(BuyButtonLayout.testButtonY(316, CARD_H, testW, testH, p, 0), CARD_H - testH - 4, "316 px: same row");
    }

    // ------------------------------------------------------------------------------------------------------------
    // Vorschau über dem Button
    // ------------------------------------------------------------------------------------------------------------

    private static void previewArea() {
        BuyButtonLayout.Placement p = BuyButtonLayout.place(110, CARD_H, textWidth("1.500"), textWidth("1,5k"));
        // 3D-Modelle: Bereich 18..96 (an der Unterkante verankert)
        int pb = BuyButtonLayout.previewBottom(p, CARD_H - 16);
        eq(pb, p.y() - BuyButtonLayout.PREVIEW_GAP, "model preview ends above the pill");
        check(pb > 18 + BuyButtonLayout.MIN_PREVIEW_HEIGHT, "enough room for the model preview");
        float s = 0.84f * (CARD_H - 22) / 2.125f;
        float fitted = BuyButtonLayout.fitScale(s, 18, CARD_H - 16, pb);
        check(Math.abs(fitted - s * (pb - 18) / (float) (CARD_H - 16 - 18)) < 1e-4, "scale shrinks proportionally");
        // An der Unterkante verankert: der höchste Punkt (bisher genau oben) bleibt genau oben
        float topBefore = (CARD_H - 16) - s * ((CARD_H - 16 - 18) / s);
        float topAfter = pb - fitted * ((CARD_H - 16 - 18) / s);
        check(Math.abs(topBefore - 18) < 1e-3 && Math.abs(topAfter - 18) < 1e-3, "top of the preview stays below the header");
        // Emotes: Bereich 14..98, mittig verankert, Größe 28
        int eb = BuyButtonLayout.previewBottom(p, CARD_H - 14);
        eq(eb, p.y() - BuyButtonLayout.PREVIEW_GAP, "emote preview ends above the pill");
        eq(Math.round(BuyButtonLayout.fitScale(28.0f, 14, CARD_H - 14, eb)), 26, "emote figure size 28 -> 26");
        // ohne Button unverändert
        eq(BuyButtonLayout.previewBottom(null, CARD_H - 16), CARD_H - 16, "no pill: preview unchanged");
        eq(BuyButtonLayout.fitScale(1.5f, 18, 96, 96), 1.5f, "same area: same scale");
        eq(BuyButtonLayout.fitScale(1.5f, 18, 96, 120), 1.5f, "never larger");
        eq(BuyButtonLayout.fitScale(1.5f, 18, 18, 10), 1.5f, "degenerate area: unchanged");
        eq(BuyButtonLayout.fitScale(1.5f, 18, 96, 10), 0.0f, "negative area: zero");
        // für jede Platzierung: Vorschau-Unterkante über der Pille und unter der Kopfzeile
        for (int w = 20; w <= 420; w++) {
            for (int price : PRICES) {
                BuyButtonLayout.Placement q = BuyButtonLayout.place(w, CARD_H,
                        textWidth(BuyButtonLayout.formatGrouped(price, '.')), textWidth(BuyButtonLayout.formatCompact(price, ',')));
                if (q == null) continue;
                int bottom = BuyButtonLayout.previewBottom(q, CARD_H - 16);
                check(bottom <= q.y() - BuyButtonLayout.PREVIEW_GAP && bottom - 18 >= BuyButtonLayout.MIN_PREVIEW_HEIGHT,
                        "preview between header and pill (" + w + ")");
            }
        }
    }
}
