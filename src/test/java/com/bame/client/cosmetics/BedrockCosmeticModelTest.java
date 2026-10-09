package com.bame.client.cosmetics;

public class BedrockCosmeticModelTest {

    public static void main(String[] args) {
        testLoadGoldWings();
        testLoadDragonWings();
        testLoadAngelHalo();
        testLoadDemonHorns();
        System.out.println("ALL COSMETIC MODEL TESTS PASSED!");
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    public static void testLoadGoldWings() {
        BedrockCosmeticModel model = BedrockCosmeticModel.load("assets/caeserclient/models/cosmetics/wings/gold_wings.geo.json");
        check(model != null, "gold_wings should load");
        check(model.bonesByName.containsKey("tl"), "tl bone should exist");
        check(model.bonesByName.containsKey("tr"), "tr bone should exist");
        check(model.bonesByName.containsKey("tll"), "tll bone should exist");
        check(model.bonesByName.containsKey("trr"), "trr bone should exist");
        check(!model.rootBones.isEmpty(), "root bones should not be empty");
        System.out.println("Gold wings loaded: " + model.bonesByName.size() + " bones, " + model.rootBones.size() + " roots");
    }

    public static void testLoadDragonWings() {
        BedrockCosmeticModel model = BedrockCosmeticModel.load("assets/caeserclient/models/cosmetics/wings/dragon_wings.geo.json");
        check(model != null, "dragon_wings should load");
        check(model.bonesByName.containsKey("L2"), "L2 bone should exist");
        check(model.bonesByName.containsKey("R2"), "R2 bone should exist");
        check(!model.rootBones.isEmpty(), "root bones should not be empty");
        System.out.println("Dragon wings loaded: " + model.bonesByName.size() + " bones, " + model.rootBones.size() + " roots");
    }

    public static void testLoadAngelHalo() {
        BedrockCosmeticModel model = BedrockCosmeticModel.load("assets/caeserclient/models/cosmetics/hat/angel_halo.geo.json");
        check(model != null, "angel_halo should load");
        check(model.bonesByName.containsKey("haloBase"), "haloBase bone should exist");
        check(!model.rootBones.isEmpty(), "root bones should not be empty");
        System.out.println("Angel halo loaded: " + model.bonesByName.size() + " bones, " + model.rootBones.size() + " roots");
    }

    public static void testLoadDemonHorns() {
        BedrockCosmeticModel model = BedrockCosmeticModel.load("assets/caeserclient/models/cosmetics/hat/demon_horns.geo.json");
        check(model != null, "demon_horns should load");
        check(!model.rootBones.isEmpty(), "root bones should not be empty");
        System.out.println("Demon horns loaded: " + model.bonesByName.size() + " bones, " + model.rootBones.size() + " roots");
    }
}
