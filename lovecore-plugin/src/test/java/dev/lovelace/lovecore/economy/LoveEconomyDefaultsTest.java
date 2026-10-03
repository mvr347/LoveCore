package dev.lovelace.lovecore.economy;

import dev.lovelace.lovecore.api.economy.Denomination;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import dev.lovelace.lovecore.api.economy.MoneyParser;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoveEconomyDefaultsTest {

    private static LoveEconomy economy(double index) {
        return new LoveEconomy() {
            public String currencyName() { return "монет"; }
            public List<Denomination> denominations() { return MoneyParser.STANDARD; }
            public long balance(Player p) { return 0; }
            public boolean has(Player p, long a) { return false; }
            public boolean charge(Player p, long a) { return false; }
            public void give(Player p, long a) { }
            public boolean canFit(Player p, long a) { return true; }
            public long valueOf(ItemStack s) { return 0; }
            @Override public double priceIndex() { return index; }
        };
    }

    @Test
    void scaledAppliesTheIndexAndKeepsPositivePricesAtLeastOne() {
        assertEquals(120, economy(1.2).scaled(100));
        assertEquals(1, economy(0.1).scaled(2));
        assertEquals(0, economy(1.5).scaled(0));
        assertEquals(100, economy(1.0).scaled(100));
    }

    @Test
    void parseUsesAllDenominations() {
        assertEquals(350, economy(1.0).parse("3i 50c"));
    }
}
