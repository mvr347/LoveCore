package dev.lovelace.lovecore.economy;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DenominationDifferencesTest {

    private static Map<String, Long> coins(long copper, long iron, long gold, long diamond) {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("copper_coin", copper);
        m.put("iron_coin", iron);
        m.put("gold_coin", gold);
        m.put("diamond_coin", diamond);
        return m;
    }

    @Test
    void sameValuesNoDifferences() {
        assertTrue(PhysicalEconomy.denominationDifferences(coins(1, 100, 2000, 20000), coins(1, 100, 2000, 20000)).isEmpty());
    }

    @Test
    void oldLadderIsReported() {
        List<String> diff = PhysicalEconomy.denominationDifferences(coins(1, 10, 50, 100), coins(1, 100, 2000, 20000));
        assertEquals(3, diff.size());
        assertTrue(diff.get(2).contains("diamond_coin=100"));
        assertTrue(diff.get(2).contains("20000"));
    }

    @Test
    void missingAndExtraCoinsAreReported() {
        Map<String, Long> inUse = new LinkedHashMap<>(coins(1, 100, 2000, 20000));
        inUse.remove("gold_coin");
        inUse.put("ruby_coin", 5L);
        List<String> diff = PhysicalEconomy.denominationDifferences(inUse, coins(1, 100, 2000, 20000));
        assertEquals(2, diff.size());
    }
}
