package dev.lovelace.lovecore.economy;

import dev.lovelace.lovecore.api.economy.Denomination;
import dev.lovelace.lovecore.api.economy.MoneyParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MoneyParserTest {

    @Test
    void parsesSuffixesAndSums() {
        assertEquals(350, MoneyParser.parse("3i 50c", null));
        assertEquals(3_000, MoneyParser.parse("1.5g", null));
        assertEquals(26_100, MoneyParser.parse("1d 3g 1i", null));
        assertEquals(250, MoneyParser.parse("250", null));
        assertEquals(4_000, MoneyParser.parse("2 gold", null));
        assertEquals(4_000, MoneyParser.parse("2 золотых", null));
        assertEquals(200, MoneyParser.parse("2 железных", null));
        assertEquals(120, MoneyParser.parse("1i+20c", null));
    }

    @Test
    void usesServerDenominationsWhenGiven() {
        List<Denomination> custom = List.of(new Denomination("iron_coin", 10), new Denomination("copper_coin", 1));
        assertEquals(30, MoneyParser.parse("3i", custom));
        // gold is missing on this server: the standard value is used
        assertEquals(2_000, MoneyParser.parse("1g", custom));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> MoneyParser.parse("", null));
        assertThrows(IllegalArgumentException.class, () -> MoneyParser.parse("abc", null));
        assertThrows(IllegalArgumentException.class, () -> MoneyParser.parse("3x", null));
        assertThrows(IllegalArgumentException.class, () -> MoneyParser.parse("3i garbage", null));
        assertThrows(IllegalArgumentException.class, () -> MoneyParser.parse("-5", null));
    }
}
