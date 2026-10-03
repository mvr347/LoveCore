package dev.lovelace.lovecore.api.economy;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбор денежных значений из конфигов: {@code "3i 50c"}, {@code "1.5g"}, {@code "2 золотых"}, {@code "250"}.
 *
 * <p>Суффиксы: {@code c} (медная), {@code i} (железная), {@code g} (золотая), {@code d} (алмазная),
 * {@code n} (незеритовая); допускаются и слова ({@code copper}, {@code iron}, {@code gold}, {@code diamond},
 * {@code netherite}) и русские первые буквы ({@code м}, {@code ж}, {@code з}, {@code а}, {@code н}) с
 * окончаниями ({@code "2 золотых"}). Число без суффикса — медные единицы. Части складываются:
 * {@code "1d 5g 20i"} = 1·20 000 + 5·2 000 + 20·100.</p>
 *
 * <p>Ценность номинала берётся из переданного списка (по id монеты); если нужного номинала в списке нет —
 * из {@link #STANDARD}. Так значения в конфигах остаются читаемыми и не ломаются при смене номиналов.</p>
 */
public final class MoneyParser {

    /** Номиналы по умолчанию (медные единицы), от старшего к младшему. */
    public static final List<Denomination> STANDARD = List.of(
            new Denomination("netherite_coin", 100_000),
            new Denomination("diamond_coin", 20_000),
            new Denomination("gold_coin", 2_000),
            new Denomination("iron_coin", 100),
            new Denomination("copper_coin", 1));

    private static final Pattern PART = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*([a-zа-яё]*)");

    private MoneyParser() {
    }

    /** Ключевое слово номинала по суффиксу, {@code null} — суффикс незнакомый. */
    static String tierOf(String suffix) {
        String s = suffix.toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.equals("c") || s.startsWith("copper") || s.startsWith("м")) return "copper";
        if (s.equals("i") || s.startsWith("iron") || s.startsWith("ж")) return "iron";
        if (s.equals("g") || s.startsWith("gold") || s.startsWith("з")) return "gold";
        if (s.equals("d") || s.startsWith("diamond") || s.startsWith("а")) return "diamond";
        if (s.equals("n") || s.startsWith("netherite") || s.startsWith("н")) return "netherite";
        return null;
    }

    private static long valueOfTier(String tier, List<Denomination> denominations) {
        if (denominations != null) {
            for (Denomination d : denominations) {
                if (d.itemId().toLowerCase(Locale.ROOT).contains(tier)) return d.value();
            }
        }
        for (Denomination d : STANDARD) {
            if (d.itemId().contains(tier)) return d.value();
        }
        throw new IllegalArgumentException("нет номинала " + tier);
    }

    /**
     * @param text          значение из конфига
     * @param denominations номиналы сервера (можно {@code null} — тогда стандартные)
     * @return сумма в медных единицах
     * @throws IllegalArgumentException пустая строка, незнакомый суффикс, мусор, переполнение
     */
    public static long parse(String text, List<Denomination> denominations) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("пустое значение");
        }
        String rest = text.trim().toLowerCase(Locale.ROOT).replace('+', ' ').replace(',', '.');
        Matcher m = PART.matcher(rest);
        long total = 0;
        int end = 0;
        boolean any = false;
        while (m.find()) {
            if (!rest.substring(end, m.start()).isBlank()) {
                throw new IllegalArgumentException("лишний текст в «" + text + "»");
            }
            end = m.end();
            String tier = tierOf(m.group(2));
            if (tier == null) {
                throw new IllegalArgumentException("неизвестный номинал «" + m.group(2) + "» в «" + text + "»");
            }
            double number = Double.parseDouble(m.group(1));
            double part = number * valueOfTier(tier, denominations);
            if (part > Long.MAX_VALUE / 4.0) {
                throw new IllegalArgumentException("слишком большое значение «" + text + "»");
            }
            total = Math.addExact(total, Math.round(part));
            any = true;
        }
        if (!any || !rest.substring(end).isBlank()) {
            throw new IllegalArgumentException("не удалось разобрать «" + text + "»");
        }
        return total;
    }
}
