package com.bame.client.gui.search;

import com.bame.client.module.ContainerSearchModule;

import java.util.ArrayList;
import java.util.List;

/**
 * Prüft ohne Minecraft die Logik der Container-Suche: Text-Normalisierung ({@link ItemSearchText}), Treffer-Regeln
 * ({@link ItemSearchQuery}/{@link ItemSearchEntry}: Wortanfang, Teilstring ab 3 Zeichen, mehrere Wörter, Umlaute,
 * IDs, Lore/Verzauberungen, Shulker-Inhalt), Position unter/über/neben dem Fenster ({@link ContainerSearchLayout}),
 * Cache-Invalidierung ({@link SlotMatchCache}), Tasten-Entscheidung ({@link ContainerSearchKeys}) und die
 * Standardwerte der Einstellungen. Gradle-Aufgabe {@code containerSearchTest} (läuft mit {@code check}/{@code build}).
 */
public final class ContainerSearchTest {
    private static int checks = 0;

    public static void main(String[] args) {
        textNormalization();
        wordMatching();
        nameRules();
        multipleWords();
        umlauts();
        registryIds();
        customNames();
        loreAndEnchantments();
        containerContents();
        emptyAndWhitespaceQueries();
        cacheInvalidation();
        cacheSimulation();
        layoutSpecificCases();
        layoutProperties();
        keyDecisions();
        settingsDefaults();
        System.out.println("CONTAINER SEARCH TESTS PASSED (" + checks + " checks)");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    private static ItemSearchEntry item(String name, String id) {
        return ItemSearchEntry.of(name, null, id, null, null);
    }

    private static boolean matches(String query, ItemSearchEntry entry) {
        return ItemSearchQuery.parse(query).matches(entry, true, true);
    }

    // ------------------------------------------------------------------------------------------------------------

    private static void textNormalization() {
        check(ItemSearchText.clean(null).isEmpty(), "null -> empty");
        check(ItemSearchText.clean("").isEmpty(), "empty stays empty");
        check(ItemSearchText.clean("Golden Apple").equals("golden apple"), "lower case");
        check(ItemSearchText.clean("§6Golden §lApple§r").equals("golden apple"), "formatting codes stripped");
        check(ItemSearchText.clean("§x§f§f§0§0§0§0Red").equals("red"), "hex colour codes stripped");
        check(ItemSearchText.clean("ᴅɪᴀᴍᴏɴᴅ ꜱᴡᴏʀᴅ").equals("diamond sword"), "small caps mapped");
        check(ItemSearchText.clean("ＧＯＬＤ").equals("gold"), "full width letters (NFKC)");
        check(ItemSearchText.clean("Golden Apple").equals("golden apple"), "no-break space -> space");
        check(ItemSearchText.clean("Golden Apple").equals("golden apple"), "narrow no-break space -> space");
        check(ItemSearchText.clean("ﬁsh").equals("fish"), "ligature fi");
        check(ItemSearchText.clean("KÜRBIS").equals("kürbis"), "umlaut kept by clean, lower cased");
        String ascii = "golden_apple";
        check(ItemSearchText.foldPlain(ascii) == ascii && ItemSearchText.foldExpanded(ascii) == ascii, "ASCII not copied");
        check(ItemSearchText.foldPlain("kürbis").equals("kurbis"), "ü -> u");
        check(ItemSearchText.foldExpanded("kürbis").equals("kuerbis"), "ü -> ue");
        check(ItemSearchText.foldPlain("schädel").equals("schadel") && ItemSearchText.foldExpanded("schädel").equals("schaedel"), "ä");
        check(ItemSearchText.foldPlain("öl").equals("ol") && ItemSearchText.foldExpanded("öl").equals("oel"), "ö");
        check(ItemSearchText.foldPlain("straße").equals("strasse") && ItemSearchText.foldExpanded("straße").equals("strasse"), "ß -> ss");
        check(ItemSearchText.foldPlain("épée").equals("epee"), "French accents");
        check(ItemSearchText.foldPlain("niño").equals("nino"), "Spanish tilde");
        check(ItemSearchText.foldPlain("æble").equals("aeble") && ItemSearchText.foldPlain("øl").equals("ol"), "æ, ø");
        check(ItemSearchText.foldExpanded("é").equals("e"), "expanded variant also strips other accents");
    }

    private static void wordMatching() {
        check(ItemSearchText.containsAtWordStart("golden apple", "app"), "second word start");
        check(ItemSearchText.containsAtWordStart("golden apple", "gol"), "first word start");
        check(!ItemSearchText.containsAtWordStart("pineapple", "app"), "inside a word is no word start");
        check(ItemSearchText.containsAtWordStart("golden_apple", "apple"), "underscore is a word boundary");
        check(ItemSearchText.containsAtWordStart("minecraft:golden_apple", "golden"), "colon is a word boundary");
        check(ItemSearchText.containsAtWordStart("jack o'lantern", "lan"), "apostrophe is a word boundary");
        check(ItemSearchText.containsAtWordStart("apple pineapple", "apple"), "first occurrence at start");
        check(ItemSearchText.containsAtWordStart("pineapple apple", "apple"), "later occurrence at a word start");
        check(ItemSearchText.matchesWord("pineapple", "apple", true), "anywhere allowed");
        check(!ItemSearchText.matchesWord("pineapple", "apple", false), "anywhere not allowed");
        check(ItemSearchText.matchesWord("anything", "", false), "empty word matches");
        check(!ItemSearchText.matchesWord("ab", "abc", true), "word longer than text");
    }

    private static void nameRules() {
        ItemSearchEntry apple = item("Golden Apple", "minecraft:golden_apple");
        check(matches("golden", apple), "full word");
        check(matches("gold", apple), "word prefix");
        check(matches("go", apple), "two letters at a word start");
        check(matches("g", apple), "one letter at a word start");
        check(matches("app", apple), "prefix of the second word");
        check(matches("a", apple), "single letter of the second word");
        check(matches("ple", apple), "3 letters inside a word (substring >= 3)");
        check(matches("den", apple), "substring inside the first word");
        check(!matches("pl", apple), "2 letters inside a word do not match");
        check(!matches("en", apple), "2 letters inside a word do not match (2)");
        check(!matches("carrot", apple), "different item");
        check(matches("GOLDEN", apple), "case insensitive");
        check(matches("Gold", apple), "mixed case");

        ItemSearchEntry rail = item("Powered Rail", "minecraft:powered_rail");
        check(matches("red", rail), "substring rule (>= 3) is literal, as specified");
        check(!matches("re", rail), "but not for 2 letters");
    }

    private static void multipleWords() {
        ItemSearchEntry apple = item("Golden Apple", "minecraft:golden_apple");
        ItemSearchEntry carrot = item("Golden Carrot", "minecraft:golden_carrot");
        check(matches("golden apple", apple), "both words");
        check(matches("apple golden", apple), "order does not matter");
        check(!matches("golden apple", carrot), "AND: apple missing");
        check(matches("gol car", carrot), "two prefixes");
        check(matches("golden   carrot", carrot), "multiple spaces");
        check(!matches("golden apple carrot", apple), "all words have to match");
        check(ItemSearchQuery.parse("  Golden   APPLE ").normalized().equals("golden apple"), "normalized form");
        check(ItemSearchQuery.parse("golden apple").wordCount() == 2, "word count");
    }

    private static void umlauts() {
        ItemSearchEntry pumpkin = item("Kürbis", "minecraft:pumpkin");
        check(matches("kürbis", pumpkin), "exact umlaut");
        check(matches("kurbis", pumpkin), "ü = u");
        check(matches("kuerbis", pumpkin), "ü = ue");
        check(matches("KÜRB", pumpkin), "upper case umlaut prefix");
        check(matches("kür", pumpkin), "umlaut prefix");
        check(matches("pumpkin", pumpkin), "English via id");

        ItemSearchEntry dye = item("Grüner Farbstoff", "minecraft:green_dye");
        check(matches("grün", dye), "grün");
        check(matches("grun", dye), "grun");
        check(matches("gruen", dye), "gruen");
        check(matches("farb", dye), "second word");
        check(matches("green", dye), "id");

        ItemSearchEntry skull = item("Skelettschädel", "minecraft:skeleton_skull");
        check(matches("schädel", skull), "umlaut substring (>= 3)");
        check(matches("schaedel", skull), "ae substring");
        check(matches("schadel", skull), "a substring");

        ItemSearchEntry written = item("Kuehltruhe", "minecraft:chest"); // Custom-Name ohne Umlaut geschrieben
        check(matches("kühl", written), "query umlaut matches text written with ue");

        ItemSearchEntry big = item("Große Truhe", "minecraft:chest");
        check(matches("große", big) && matches("grosse", big) && matches("gro", big), "ß = ss");

        ItemSearchEntry apple = item("Goldener Apfel", "minecraft:golden_apple");
        check(matches("golden", apple), "German name: word starts with query");
        check(matches("apfel", apple), "German second word");
        check(matches("apple", apple), "English name via registry id");
        check(matches("gold apf", apple), "two German prefixes");
    }

    private static void registryIds() {
        ItemSearchEntry apple = item("Goldener Apfel", "minecraft:golden_apple");
        check(matches("golden_apple", apple), "full path with underscore");
        check(matches("minecraft:golden_apple", apple), "full id with namespace");
        check(matches("minecraft:", apple), "namespace with colon");
        check(!matches("minecraft", apple), "namespace without colon does not match everything");
        check(!matches("mine", apple), "namespace prefix does not match everything");
        check(!matches("mi", apple), "two letters of the namespace do not match");
        check(matches("apple", apple), "id word after underscore");
        check(matches("ap", apple), "id word start after underscore");

        ItemSearchEntry modded = item("Brass Ingot", "create:brass_ingot");
        check(matches("create:", modded), "mod namespace with colon");
        check(matches("create:brass", modded), "mod namespace + path");
        check(!matches("create", modded), "mod namespace without colon");

        ItemSearchEntry noNamespace = item("Stone", "stone");
        check(matches("minecraft:stone", noNamespace), "missing namespace means minecraft");
        check(matches("stone", noNamespace), "path");
    }

    private static void customNames() {
        ItemSearchEntry sword = ItemSearchEntry.of("Excalibur", "Diamantschwert", "minecraft:diamond_sword", null, null);
        check(matches("excal", sword), "custom name");
        check(matches("schwert", sword), "base name of a renamed item (substring)");
        check(matches("diamant", sword), "base name prefix");
        check(matches("diamond", sword), "id");
        check(matches("excalibur sword", sword), "custom name + id word");

        ItemSearchEntry fancy = ItemSearchEntry.of("§6§lᴅɪᴀᴍᴏɴᴅ ꜱᴡᴏʀᴅ §7(Rare)", "Diamond Sword", "minecraft:diamond_sword", null, null);
        check(matches("diamond sword", fancy), "small caps server name");
        check(matches("rare", fancy), "word in brackets");
        check(matches("§6diamond", fancy), "formatting codes in the query are ignored too");
        check(!matches("6l", fancy), "letters of formatting codes are not part of the name");

        ItemSearchEntry same = ItemSearchEntry.of("Stick", "Stick", "minecraft:stick", null, null);
        check(same.nameFields.length == 1, "identical base name is stored once");
    }

    private static void loreAndEnchantments() {
        List<String> extras = List.of("Sharpness V", "sharpness", "Fire Aspect II", "fire_aspect", "§7Price: 100 coins", "Click to buy");
        ItemSearchEntry sword = ItemSearchEntry.of("Diamond Sword", null, "minecraft:diamond_sword", extras, null);
        ItemSearchQuery sharp = ItemSearchQuery.parse("sharp");
        check(sharp.matches(sword, true, false), "enchantment name prefix");
        check(!sharp.matches(sword, false, false), "enchantments only with the setting");
        check(ItemSearchQuery.parse("aspect").matches(sword, true, false), "second word of an enchantment");
        check(ItemSearchQuery.parse("fire_aspect").matches(sword, true, false), "enchantment id");
        check(ItemSearchQuery.parse("price").matches(sword, true, false), "lore word");
        check(ItemSearchQuery.parse("pri").matches(sword, true, false), "lore word prefix");
        check(!ItemSearchQuery.parse("ice").matches(sword, true, false), "lore only at word starts (Price != ice)");
        check(!ItemSearchQuery.parse("lick").matches(sword, true, false), "lore only at word starts (Click != lick)");
        check(ItemSearchQuery.parse("coins").matches(sword, true, false), "lore after formatting code");
        check(ItemSearchQuery.parse("diamond sharp").matches(sword, true, false), "words in different fields (AND)");
        check(!ItemSearchQuery.parse("diamond sharp").matches(sword, false, false), "AND fails without extras");
        check(!ItemSearchQuery.parse("price").matches(sword, false, false), "lore only with the setting");

        ItemSearchEntry book = ItemSearchEntry.of("Verzaubertes Buch", null, "minecraft:enchanted_book", List.of("Schärfe V", "sharpness"), null);
        check(ItemSearchQuery.parse("schärfe").matches(book, true, false), "German enchantment");
        check(ItemSearchQuery.parse("schaerfe").matches(book, true, false), "German enchantment ae");
        check(ItemSearchQuery.parse("sharp").matches(book, true, false), "English enchantment id in a German game");

        ItemSearchEntry blank = ItemSearchEntry.of("Dirt", null, "minecraft:dirt", java.util.Arrays.asList("", null, "  "), null);
        check(blank.extraFields.length == 0, "blank lore lines are skipped");
    }

    private static void containerContents() {
        ItemSearchEntry apple = item("Golden Apple", "minecraft:golden_apple");
        ItemSearchEntry dirt = item("Dirt", "minecraft:dirt");
        ItemSearchEntry shulker = ItemSearchEntry.of("Shulker Box", null, "minecraft:shulker_box", null, List.of(apple, dirt));
        check(shulker.hasChildren() && shulker.childCount() == 2, "children stored");
        check(ItemSearchQuery.parse("golden").matches(shulker, true, true), "content matches");
        check(ItemSearchQuery.parse("dirt").matches(shulker, true, true), "second content");
        check(ItemSearchQuery.parse("shulker").matches(shulker, true, true), "container itself");
        check(!ItemSearchQuery.parse("golden").matches(shulker, true, false), "content only with the setting");
        check(ItemSearchQuery.parse("shulker").matches(shulker, true, false), "container itself without the setting");
        check(!ItemSearchQuery.parse("golden dirt").matches(shulker, true, true), "one content item has to match all words");
        check(!ItemSearchQuery.parse("shulker golden").matches(shulker, true, true), "container and content words are not mixed");

        ItemSearchEntry diamond = item("Diamond", "minecraft:diamond");
        ItemSearchEntry bundle = ItemSearchEntry.of("Bundle", null, "minecraft:bundle", null, List.of(diamond));
        ItemSearchEntry outer = ItemSearchEntry.of("Shulker Box", null, "minecraft:shulker_box", null, List.of(dirt, bundle));
        check(ItemSearchQuery.parse("diamond").matches(outer, true, true), "nested bundle inside a shulker");

        List<String> lore = List.of("Sharpness V");
        ItemSearchEntry sword = ItemSearchEntry.of("Diamond Sword", null, "minecraft:diamond_sword", lore, null);
        ItemSearchEntry box = ItemSearchEntry.of("Shulker Box", null, "minecraft:shulker_box", null, List.of(sword));
        check(ItemSearchQuery.parse("sharp").matches(box, true, true), "enchantment of a content item");
        check(!ItemSearchQuery.parse("sharp").matches(box, false, true), "content enchantments need the lore setting");

        List<ItemSearchEntry> many = new ArrayList<>();
        for (int i = 0; i < ItemSearchEntry.MAX_CHILDREN + 50; i++) many.add(dirt);
        ItemSearchEntry huge = ItemSearchEntry.of("Crate", null, "mod:crate", null, many);
        check(huge.childCount() == ItemSearchEntry.MAX_CHILDREN, "children capped");
        List<ItemSearchEntry> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(apple);
        check(ItemSearchEntry.of("Box", null, "x:box", null, withNull).childCount() == 1, "null children skipped");
    }

    private static void emptyAndWhitespaceQueries() {
        ItemSearchEntry apple = item("Golden Apple", "minecraft:golden_apple");
        check(ItemSearchQuery.parse("").isEmpty(), "empty");
        check(ItemSearchQuery.parse(null).isEmpty(), "null");
        check(ItemSearchQuery.parse("    ").isEmpty(), "spaces only");
        check(ItemSearchQuery.parse("§6").isEmpty(), "formatting code only");
        check(ItemSearchQuery.EMPTY.matches(apple, false, false), "empty query matches everything");
        check(ItemSearchQuery.EMPTY.matches(null, false, false), "empty query matches even without data");
        check(!ItemSearchQuery.parse("gold").matches(null, true, true), "no data, no match");
        check(ItemSearchQuery.parse("golden\tapple").wordCount() == 2, "tab separates words");
        check(ItemSearchQuery.parse("golden apple").wordCount() == 2, "no-break space separates words");
        check(matches("golden\napple", apple), "newline separates words");
    }

    // ------------------------------------------------------------------------------------------------------------

    private static void cacheInvalidation() {
        SlotMatchCache cache = new SlotMatchCache();
        cache.ensureCapacity(54);
        check(cache.capacity() >= 54, "capacity");
        Object a = new Object();
        Object b = new Object();
        Object entryA = "entryA";
        check(cache.needsEntry(0, a), "fresh slot needs an entry");
        check(cache.state(0) == SlotMatchCache.UNKNOWN, "fresh slot unknown");
        cache.putEntry(0, a, null, entryA);
        check(!cache.needsEntry(0, a), "same stack: entry kept");
        check(cache.entry(0) == entryA, "entry stored");
        check(cache.needsMatch(0), "new entry needs a match check");
        cache.putMatch(0, true);
        check(!cache.needsMatch(0), "match stored");
        check(cache.state(0) == SlotMatchCache.MATCH, "state MATCH");

        cache.invalidateMatches();
        check(cache.needsMatch(0), "new query: match check again");
        check(!cache.needsEntry(0, a), "new query: entry kept (no text work)");
        check(cache.entry(0) == entryA, "entry still there");
        cache.putMatch(0, false);
        check(cache.state(0) == SlotMatchCache.NO_MATCH, "state NO_MATCH");

        check(cache.needsEntry(0, b), "different stack instance: rebuild");
        cache.putEntry(0, b, null, "entryB");
        check(cache.state(0) == SlotMatchCache.UNKNOWN && cache.needsMatch(0), "rebuild resets the match");

        Object ref = new Object();
        cache.putEntry(1, a, ref, "box");
        check(cache.contentRef(1) == ref, "content reference stored (shulker/bundle)");
        check(cache.contentRef(0) == null, "no content reference for normal items");

        cache.putMatch(0, true);
        cache.putMatch(1, true);
        cache.invalidateEntries();
        check(cache.needsEntry(0, b) && cache.needsEntry(1, a), "settings change: rebuild all");
        check(cache.needsMatch(0) && cache.needsMatch(1), "settings change: match all");

        cache.putEntry(5, a, null, "five");
        cache.ensureCapacity(200);
        check(cache.capacity() >= 200, "capacity grows");
        check(!cache.needsEntry(5, a) && "five".equals(cache.entry(5)), "growth keeps entries");
        check(cache.needsEntry(150, a), "new places are invalid");

        cache.clear();
        check(cache.needsEntry(5, a) && cache.entry(5) == null && cache.state(5) == SlotMatchCache.UNKNOWN, "clear frees all");
        check(cache.state(-1) == SlotMatchCache.UNKNOWN && cache.state(100000) == SlotMatchCache.UNKNOWN, "out of range");
    }

    /** Ablauf wie im Spiel: gebaut wird nur bei neuem Slot-Inhalt, ein neuer Suchtext prüft nur neu. */
    private static void cacheSimulation() {
        final int slots = 63; // große Truhe + Spieler-Inventar
        Object[] stacks = new Object[slots];
        ItemSearchEntry[] data = new ItemSearchEntry[slots];
        for (int i = 0; i < slots; i++) {
            stacks[i] = new Object();
            data[i] = i % 3 == 0 ? item("Golden Apple", "minecraft:golden_apple") : item("Cobblestone", "minecraft:cobblestone");
        }
        SlotMatchCache cache = new SlotMatchCache();
        int[] builds = {0};
        int[] checksDone = {0};
        ItemSearchQuery[] query = {ItemSearchQuery.parse("golden")};

        Runnable frame = () -> {
            cache.ensureCapacity(slots);
            for (int i = 0; i < slots; i++) {
                if (cache.needsEntry(i, stacks[i])) {
                    builds[0]++;
                    cache.putEntry(i, stacks[i], null, data[i]);
                }
                if (cache.needsMatch(i)) {
                    checksDone[0]++;
                    cache.putMatch(i, query[0].matches((ItemSearchEntry) cache.entry(i), true, true));
                }
            }
        };

        frame.run();
        check(builds[0] == slots && checksDone[0] == slots, "first frame builds everything once");
        int matchCount = 0;
        for (int i = 0; i < slots; i++) if (cache.state(i) == SlotMatchCache.MATCH) matchCount++;
        check(matchCount == 21, "21 golden apples found");

        for (int f = 0; f < 100; f++) frame.run();
        check(builds[0] == slots && checksDone[0] == slots, "unchanged frames do no work");

        query[0] = ItemSearchQuery.parse("cobble");
        cache.invalidateMatches();
        frame.run();
        check(builds[0] == slots, "new query: no rebuild");
        check(checksDone[0] == 2 * slots, "new query: every slot checked once");
        check(cache.state(1) == SlotMatchCache.MATCH && cache.state(0) == SlotMatchCache.NO_MATCH, "new result");

        stacks[7] = new Object();
        data[7] = item("Golden Apple", "minecraft:golden_apple");
        frame.run();
        check(builds[0] == slots + 1 && checksDone[0] == 2 * slots + 1, "one changed slot: one rebuild, one check");
        check(cache.state(7) == SlotMatchCache.NO_MATCH, "changed slot re-evaluated");
    }

    // ------------------------------------------------------------------------------------------------------------

    private static ContainerSearchLayout.Placement placeCentered(int sw, int sh, int gw, int gh) {
        return ContainerSearchLayout.place(sw, sh, (sw - gw) / 2, (sh - gh) / 2, gw, gh);
    }

    private static void layoutSpecificCases() {
        int w = ContainerSearchLayout.PREFERRED_WIDTH;
        check(w >= 120 && w <= 140, "width 120-140 px as requested");

        // 1920x1080, GUI-Skalierung 4 -> 480x270: große Truhe passt darunter
        ContainerSearchLayout.Placement p = placeCentered(480, 270, 176, 222);
        check(p.mode() == ContainerSearchLayout.BELOW, "large chest at 480x270: below");
        check(p.y() == 24 + 222 + ContainerSearchLayout.GAP, "directly below the window");
        check(p.x() == 152 + (176 - w) / 2 && p.width() == w, "centered below the window");

        // kleine Truhe, Trichter, Spender, Inventar bei 427x240: immer darunter
        check(placeCentered(427, 240, 176, 168).mode() == ContainerSearchLayout.BELOW, "small chest below");
        check(placeCentered(427, 240, 176, 133).mode() == ContainerSearchLayout.BELOW, "hopper below");
        check(placeCentered(427, 240, 176, 166).mode() == ContainerSearchLayout.BELOW, "dispenser/shulker/inventory below");
        check(placeCentered(320, 240, 176, 166).mode() == ContainerSearchLayout.BELOW, "166 high GUI below even at 320x240");

        // große Truhe bei 427x240: weder darunter noch darüber Platz -> rechts daneben, unten bündig
        p = placeCentered(427, 240, 176, 222);
        check(p.mode() == ContainerSearchLayout.RIGHT, "large chest at 427x240: right side");
        check(p.x() == 125 + 176 + ContainerSearchLayout.GAP, "right of the window");
        check(p.y() == 9 + 222 - ContainerSearchLayout.HEIGHT, "aligned with the bottom of the window");
        check(p.width() == 121, "uses the free width");

        // kleinster Auto-Bildschirm 320x240: rechts reicht es noch
        p = placeCentered(320, 240, 176, 222);
        check(p.mode() == ContainerSearchLayout.RIGHT && p.width() >= ContainerSearchLayout.MIN_WIDTH, "320x240 large chest: right");

        // Fenster weiter unten (nicht mittig): darüber
        p = ContainerSearchLayout.place(427, 240, 125, 100, 176, 130);
        check(p.mode() == ContainerSearchLayout.ABOVE, "no room below -> above");
        check(p.y() + p.height() + ContainerSearchLayout.GAP == 100, "directly above the window");

        // rechts zu wenig Platz, links genug: links daneben
        p = ContainerSearchLayout.place(427, 240, 240, 9, 176, 222);
        check(p.mode() == ContainerSearchLayout.LEFT, "left side when the right side is full");
        check(p.x() + p.width() + ContainerSearchLayout.GAP == 240, "left of the window");

        // winziger Screen: letzter Ausweg am unteren Rand
        p = ContainerSearchLayout.place(200, 240, 12, 9, 176, 222);
        check(p.mode() == ContainerSearchLayout.OVERLAP, "tiny screen: overlap");
        check(p.y() + p.height() + ContainerSearchLayout.MARGIN == 240, "overlap at the bottom edge");

        // Rezeptbuch verschiebt das Inventar nach rechts: das Feld folgt
        ContainerSearchLayout.Placement closed = ContainerSearchLayout.place(640, 360, 232, 97, 176, 166);
        ContainerSearchLayout.Placement open = ContainerSearchLayout.place(640, 360, 309, 97, 176, 166);
        check(open.x() - closed.x() == 77 && open.y() == closed.y(), "field follows the window");

        check(placeCentered(480, 270, 176, 222).equals(placeCentered(480, 270, 176, 222)), "deterministic");
        check(p.contains(p.x(), p.y()) && !p.contains(p.x() + p.width(), p.y()) && !p.contains(p.x() - 1, p.y()), "contains");
    }

    private static boolean intersects(ContainerSearchLayout.Placement p, int x, int y, int w, int h) {
        return p.x() < x + w && x < p.x() + p.width() && p.y() < y + h && y < p.y() + p.height();
    }

    private static void layoutProperties() {
        int[][] screens = {
                {320, 240}, {321, 241}, {360, 240}, {427, 240}, {480, 270}, {512, 288}, {640, 360}, {683, 384},
                {854, 480}, {960, 540}, {1280, 720}, {1920, 1080}, {400, 300}, {300, 400}
        };
        int[][] guis = {{176, 222}, {176, 204}, {176, 186}, {176, 168}, {176, 150}, {176, 132}, {176, 133}, {176, 166}, {195, 136}, {256, 256}};
        int[] shifts = {0, 77, -40};
        for (int[] s : screens) {
            for (int[] g : guis) {
                for (int shift : shifts) {
                    int sw = s[0], sh = s[1], gw = g[0], gh = g[1];
                    int gx = (sw - gw) / 2 + shift;
                    int gy = (sh - gh) / 2;
                    ContainerSearchLayout.Placement p = ContainerSearchLayout.place(sw, sh, gx, gy, gw, gh);
                    String what = sw + "x" + sh + " gui " + gw + "x" + gh + " shift " + shift + " -> " + p;
                    check(p.height() == ContainerSearchLayout.HEIGHT, "height " + what);
                    check(p.width() >= 1 && p.width() <= ContainerSearchLayout.PREFERRED_WIDTH, "width range " + what);
                    check(p.x() >= 0 && p.x() + p.width() <= sw, "inside horizontally " + what);
                    check(p.y() >= 0 && p.y() + p.height() <= sh, "inside vertically " + what);
                    boolean belowFits = gy + gh + ContainerSearchLayout.GAP + ContainerSearchLayout.HEIGHT + ContainerSearchLayout.MARGIN <= sh;
                    if (belowFits) check(p.mode() == ContainerSearchLayout.BELOW, "below preferred " + what);
                    switch (p.mode()) {
                        case ContainerSearchLayout.BELOW -> {
                            check(p.y() == gy + gh + ContainerSearchLayout.GAP, "below position " + what);
                            int center = p.x() + p.width() / 2;
                            boolean clamped = p.x() == ContainerSearchLayout.MARGIN || p.x() + p.width() == sw - ContainerSearchLayout.MARGIN;
                            check(clamped || Math.abs(center - (gx + gw / 2)) <= 1, "below centered " + what);
                        }
                        case ContainerSearchLayout.ABOVE -> {
                            check(!belowFits, "above only without room below " + what);
                            check(p.y() + p.height() + ContainerSearchLayout.GAP == gy, "above position " + what);
                        }
                        case ContainerSearchLayout.RIGHT -> {
                            check(p.x() == gx + gw + ContainerSearchLayout.GAP, "right position " + what);
                            check(p.width() >= ContainerSearchLayout.MIN_WIDTH, "right min width " + what);
                        }
                        case ContainerSearchLayout.LEFT -> {
                            check(p.x() + p.width() + ContainerSearchLayout.GAP == gx, "left position " + what);
                            check(p.width() >= ContainerSearchLayout.MIN_WIDTH, "left min width " + what);
                        }
                        default -> check(p.mode() == ContainerSearchLayout.OVERLAP, "known mode " + what);
                    }
                    if (p.mode() != ContainerSearchLayout.OVERLAP) {
                        check(!intersects(p, gx, gy, gw, gh), "does not cover the window " + what);
                        check(p.x() >= ContainerSearchLayout.MARGIN && p.x() + p.width() <= sw - ContainerSearchLayout.MARGIN, "screen margin x " + what);
                        check(p.y() >= ContainerSearchLayout.MARGIN && p.y() + p.height() <= sh - ContainerSearchLayout.MARGIN, "screen margin y " + what);
                    }
                    // Auto-GUI-Skalierung liefert mindestens 320x240: dort wird das Fenster nie überdeckt
                    if (sw >= 320 && sh >= 240 && gw == 176 && shift == 0) {
                        check(p.mode() != ContainerSearchLayout.OVERLAP, "vanilla containers never overlapped " + what);
                    }
                }
            }
        }
        // entartete Eingaben stürzen nicht ab
        ContainerSearchLayout.Placement tiny = ContainerSearchLayout.place(10, 10, 0, 0, 176, 222);
        check(tiny.width() >= 1 && tiny.y() >= 0, "tiny screen does not crash");
        ContainerSearchLayout.Placement zero = ContainerSearchLayout.place(0, 0, 0, 0, 0, 0);
        check(zero.width() >= 1, "zero screen does not crash");
    }

    // ------------------------------------------------------------------------------------------------------------

    private static void keyDecisions() {
        final int ctrl = ContainerSearchKeys.MOD_CONTROL;
        final int shift = ContainerSearchKeys.MOD_SHIFT;
        final int alt = ContainerSearchKeys.MOD_ALT;
        final int cmd = ContainerSearchKeys.MOD_SUPER;
        final int keyE = 69, keyQ = 81, keyW = 87, keyA = 65, keyS = 83, keyD = 68, keySpace = 32;

        // Feld fokussiert: Inventar-Tasten tippen nur
        check(ContainerSearchKeys.decide(true, true, keyE, 0) == ContainerSearchKeys.EDIT, "E does not close while typing");
        for (int digit = 49; digit <= 57; digit++) {
            check(ContainerSearchKeys.decide(true, true, digit, 0) == ContainerSearchKeys.EDIT, "digit " + digit + " does not swap");
        }
        check(ContainerSearchKeys.decide(true, true, keyQ, 0) == ContainerSearchKeys.EDIT, "Q does not drop");
        check(ContainerSearchKeys.decide(true, true, keyQ, ctrl) == ContainerSearchKeys.EDIT, "Ctrl+Q does not drop the stack");
        check(ContainerSearchKeys.decide(true, true, ContainerSearchKeys.KEY_F, 0) == ContainerSearchKeys.EDIT, "F does not swap to offhand");
        for (int key : new int[]{keyW, keyA, keyS, keyD, keySpace}) {
            check(ContainerSearchKeys.decide(true, true, key, 0) == ContainerSearchKeys.EDIT, "movement key " + key + " is text");
        }
        check(ContainerSearchKeys.decide(true, true, ContainerSearchKeys.KEY_ESCAPE, 0) == ContainerSearchKeys.PASS, "Esc closes like vanilla");
        check(ContainerSearchKeys.decide(true, true, ContainerSearchKeys.KEY_ENTER, 0) == ContainerSearchKeys.UNFOCUS, "Enter unfocuses");
        check(ContainerSearchKeys.decide(true, true, ContainerSearchKeys.KEY_KP_ENTER, 0) == ContainerSearchKeys.UNFOCUS, "keypad Enter unfocuses");
        check(ContainerSearchKeys.decide(true, true, ContainerSearchKeys.KEY_TAB, 0) == ContainerSearchKeys.UNFOCUS, "Tab unfocuses");
        check(ContainerSearchKeys.decide(true, true, ContainerSearchKeys.KEY_TAB, shift) == ContainerSearchKeys.UNFOCUS, "Shift+Tab unfocuses");
        check(ContainerSearchKeys.decide(true, true, ContainerSearchKeys.KEY_F, ctrl) == ContainerSearchKeys.EDIT, "Ctrl+F while typing stays in the field");
        check(ContainerSearchKeys.decide(true, true, 65, ctrl) == ContainerSearchKeys.EDIT, "Ctrl+A goes to the field");

        // Feld nicht fokussiert: Vanilla, außer Strg+F
        check(ContainerSearchKeys.decide(false, true, keyE, 0) == ContainerSearchKeys.PASS, "E closes when not typing");
        check(ContainerSearchKeys.decide(false, true, ContainerSearchKeys.KEY_F, 0) == ContainerSearchKeys.PASS, "F swaps when not typing");
        check(ContainerSearchKeys.decide(false, true, ContainerSearchKeys.KEY_F, ctrl) == ContainerSearchKeys.FOCUS, "Ctrl+F focuses");
        check(ContainerSearchKeys.decide(false, true, ContainerSearchKeys.KEY_F, cmd) == ContainerSearchKeys.FOCUS, "Cmd+F focuses (Mac)");
        check(ContainerSearchKeys.decide(false, true, ContainerSearchKeys.KEY_F, ctrl | shift) == ContainerSearchKeys.PASS, "Ctrl+Shift+F is not the shortcut");
        check(ContainerSearchKeys.decide(false, true, ContainerSearchKeys.KEY_F, ctrl | alt) == ContainerSearchKeys.PASS, "Ctrl+Alt+F is not the shortcut");
        check(ContainerSearchKeys.decide(false, true, ContainerSearchKeys.KEY_ESCAPE, 0) == ContainerSearchKeys.PASS, "Esc when not typing");
        check(ContainerSearchKeys.decide(false, true, ContainerSearchKeys.KEY_TAB, 0) == ContainerSearchKeys.PASS, "Tab when not typing");

        // Feld unsichtbar (z. B. Rezeptbuch verdeckt das Inventar)
        check(ContainerSearchKeys.decide(true, false, keyE, 0) == ContainerSearchKeys.UNFOCUS_PASS, "invisible field gives up focus");
        check(ContainerSearchKeys.decide(false, false, ContainerSearchKeys.KEY_F, ctrl) == ContainerSearchKeys.PASS, "invisible field cannot be focused");
    }

    private static void settingsDefaults() {
        check(ContainerSearchModule.enabled, "enabled by default");
        check(!ContainerSearchModule.expanded, "collapsed by default");
        check(!ContainerSearchModule.playerInventory, "player inventory off by default");
        check(ContainerSearchModule.searchLoreEnchantments, "lore & enchantments on by default");
        check(ContainerSearchModule.searchShulkers, "shulker contents on by default");
        check(ContainerSearchModule.highlightMatches, "highlight on by default");
        check(!ContainerSearchModule.rememberSearch, "remember search off by default");
        int base = ContainerSearchModule.dataSignature();

        ContainerSearchModule.enabled = false;
        ContainerSearchModule.playerInventory = true;
        ContainerSearchModule.searchLoreEnchantments = false;
        check(ContainerSearchModule.dataSignature() != base, "lore setting changes the data signature");
        ContainerSearchModule.searchShulkers = false;
        int both = ContainerSearchModule.dataSignature();
        ContainerSearchModule.highlightMatches = false;
        check(ContainerSearchModule.dataSignature() == both, "highlight does not change item data");
        ContainerSearchModule.rememberSearch = true;
        ContainerSearchModule.resetOptions();
        check(!ContainerSearchModule.playerInventory && ContainerSearchModule.searchLoreEnchantments
                && ContainerSearchModule.searchShulkers && ContainerSearchModule.highlightMatches
                && !ContainerSearchModule.rememberSearch, "reset restores the defaults");
        check(!ContainerSearchModule.enabled, "reset keeps on/off");
        check(ContainerSearchModule.dataSignature() == base, "signature back to default");
        ContainerSearchModule.enabled = true;
    }
}
