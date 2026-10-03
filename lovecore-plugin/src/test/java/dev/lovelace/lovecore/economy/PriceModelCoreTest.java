package dev.lovelace.lovecore.economy;

import dev.lovelace.lovecore.economy.PriceModelCore.Config;
import dev.lovelace.lovecore.economy.PriceModelCore.Ingredient;
import dev.lovelace.lovecore.economy.PriceModelCore.Kind;
import dev.lovelace.lovecore.economy.PriceModelCore.Recipe;
import dev.lovelace.lovecore.economy.PriceModelCore.Result;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PriceModelCoreTest {

    private static Recipe craft(String out, int n, Object... inputs) {
        List<Ingredient> list = new ArrayList<>();
        for (int i = 0; i < inputs.length; i += 2) {
            list.add(new Ingredient(List.of((String) inputs[i]), (Integer) inputs[i + 1]));
        }
        return new Recipe(out, n, list, Kind.CRAFT);
    }

    private static Config cfg() {
        Config c = new Config();
        c.anchors.put("DIAMOND", 100.0);
        c.anchors.put("STICK", 2.0);
        c.anchors.put("RAW_IRON", 12.0);
        c.anchors.put("GOLD_INGOT", 30.0);
        c.defaultMarkupPercent = 15;
        c.smeltFuelCost = 4;
        return c;
    }

    @Test
    void diamondGearFollowsDiamondPrice() {
        List<Recipe> recipes = List.of(
                craft("DIAMOND_CHESTPLATE", 1, "DIAMOND", 8),
                craft("DIAMOND_PICKAXE", 1, "DIAMOND", 3, "STICK", 2));
        Result r = PriceModelCore.compute(cfg(), recipes);
        assertEquals(100, r.prices().get("DIAMOND"));
        long chest = r.prices().get("DIAMOND_CHESTPLATE");
        long pick = r.prices().get("DIAMOND_PICKAXE");
        // 8 diamonds + 15 % = 920: close to eight diamonds, nowhere near a diamond coin
        assertTrue(chest >= 8 * 100 && chest <= 8 * 100 * 1.2, "chestplate " + chest);
        assertTrue(pick >= 3 * 100 && pick < chest);
    }

    @Test
    void blockIsNineIngotsWithoutMarkupAndCycleIsStable() {
        Config c = cfg();
        List<Recipe> recipes = List.of(
                new Recipe("IRON_INGOT", 1, List.of(new Ingredient(List.of("RAW_IRON"), 1)), Kind.SMELT),
                craft("IRON_BLOCK", 1, "IRON_INGOT", 9),
                craft("IRON_INGOT", 9, "IRON_BLOCK", 1),
                craft("IRON_NUGGET", 9, "IRON_INGOT", 1),
                craft("IRON_INGOT", 1, "IRON_NUGGET", 9));
        Result r = PriceModelCore.compute(c, recipes);
        // 12 raw + 4 fuel = 16; the nugget cycle (ingot -> 9 nuggets -> ingot) must not lower it
        assertEquals(16, r.prices().get("IRON_INGOT"));
        // a block is nine ingots without craft markup: 144, rounded to a step of 5
        assertEquals(PriceModelCore.round(c, 9 * 16), r.prices().get("IRON_BLOCK"));
        assertEquals(145, r.prices().get("IRON_BLOCK"));
    }

    @Test
    void cheapestAmongChoicesIsUsed() {
        Config c = cfg();
        c.anchors.put("OAK_LOG", 4.0);
        c.anchors.put("DARK_OAK_LOG", 10.0);
        Recipe planks = new Recipe("PLANKS_ANY", 4, List.of(new Ingredient(List.of("OAK_LOG", "DARK_OAK_LOG"), 1)), Kind.CRAFT);
        Result r = PriceModelCore.compute(c, List.of(planks));
        // 4 / 4 x 1.15 = 1.15 -> 1
        assertEquals(1, r.prices().get("PLANKS_ANY"));
    }

    @Test
    void overrideBeatsEverythingAndUnpricedIsReported() {
        Config c = cfg();
        c.overrides.put("DIAMOND", 250L);
        Result r = PriceModelCore.compute(c, List.of(craft("MYSTERY", 1, "UNKNOWN_THING", 2)));
        assertEquals(250, r.prices().get("DIAMOND"));
        assertTrue(r.unpriced().contains("MYSTERY"));
        assertNull(r.prices().get("MYSTERY"));
    }

    @Test
    void capsAndRoundingApply() {
        Config c = cfg();
        c.maxPrice = 500;
        Result r = PriceModelCore.compute(c, List.of(craft("BIG", 1, "DIAMOND", 20)));
        assertEquals(500, r.prices().get("BIG"));
        assertEquals(1, PriceModelCore.round(c, 0.2));
        assertEquals(1230, PriceModelCore.round(c, 1234)); // step 10 up to 2000
        assertEquals(2050, PriceModelCore.round(c, 2040)); // step 50 above 2000
    }

    @Test
    void explainShowsTheChain() {
        Result r = PriceModelCore.compute(cfg(), List.of(craft("DIAMOND_CHESTPLATE", 1, "DIAMOND", 8)));
        List<String> lines = PriceModelCore.explain(r, "DIAMOND_CHESTPLATE", 2);
        assertTrue(lines.get(0).startsWith("DIAMOND_CHESTPLATE = "));
        assertTrue(lines.stream().anyMatch(l -> l.contains("DIAMOND = 100")));
    }
}
