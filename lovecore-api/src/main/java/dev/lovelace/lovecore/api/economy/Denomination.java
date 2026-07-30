package dev.lovelace.lovecore.api.economy;

import java.util.Objects;

/**
 * Один номинал монеты: ItemsAdder-предмет и сколько единиц валюты стоит один такой предмет.
 *
 * @param itemId ID предмета ItemsAdder ({@code "gold_coin"} или {@code "currency:gold_coin"} —
 *               с namespace и без него сравнивается одинаково)
 * @param value  сколько единиц валюты стоит один предмет этого номинала
 */
public record Denomination(String itemId, long value) {

    public Denomination {
        Objects.requireNonNull(itemId, "itemId");
        if (value <= 0) {
            throw new IllegalArgumentException("value должен быть больше нуля: " + value);
        }
    }
}
