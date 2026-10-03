package dev.lovelace.lovecore.api.economy;

import dev.lovelace.lovecore.api.LoveCore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Optional;

/**
 * Единый читатель денежных ключей конфигов плагинов экосистемы: значение может быть числом
 * (медные единицы) или строкой вида {@code "3i 50c"} (см. {@link MoneyParser}). Ошибка в значении не
 * роняет плагин: в лог идёт понятная строка, возвращается значение по умолчанию.
 */
public final class MoneyConfig {

    private MoneyConfig() {
    }

    /** Сумма из конфига без индекса цен. */
    public static long get(ConfigurationSection section, String path, long def) {
        if (section == null || !section.contains(path)) return def;
        Object raw = section.get(path);
        if (raw == null) return def;
        if (raw instanceof Number n) {
            return n.longValue();
        }
        List<Denomination> denominations = LoveCore.service(LoveEconomy.class)
                .map(LoveEconomy::allDenominations).orElse(null);
        try {
            return MoneyParser.parse(String.valueOf(raw), denominations);
        } catch (IllegalArgumentException e) {
            Bukkit.getLogger().warning("[LoveCore] Денежный ключ " + section.getCurrentPath() + "." + path
                    + ": " + e.getMessage() + " - использую " + def);
            return def;
        }
    }

    /** Сумма из конфига с общим индексом цен ({@link LoveEconomy#scaled(long)}). */
    public static long getScaled(ConfigurationSection section, String path, long def) {
        long base = get(section, path, def);
        Optional<LoveEconomy> eco = LoveCore.service(LoveEconomy.class);
        return eco.map(e -> e.scaled(base)).orElse(base);
    }
}
