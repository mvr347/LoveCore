package dev.lovelace.lovecore.api.provider;

import java.util.Locale;

/**
 * Разворачивает значение из конфига плагина с учётом общего выбора ядра. Отдельным классом без
 * зависимостей от сервера, чтобы правило проверялось без запущенного Paper.
 */
public final class ProviderChoice {

    /** Значение общего выбора: все плагины берут провайдера LoveEssential. */
    public static final String GLOBAL_LOVEESSENTIAL = ProviderKind.LOVE_ESSENTIAL;

    /** Значение общего выбора: все плагины берут внешние Citizens, CMI и ReviveMe. */
    public static final String GLOBAL_EXTERNAL = "external";

    /** Значение в конфиге плагина, которое отдаёт решение общему выбору ядра. */
    public static final String FOLLOW_GLOBAL = "global";

    private ProviderChoice() {
    }

    /** Приводит значение общего выбора к {@code loveessential} или {@code external}; всё остальное — {@code external}. */
    public static String normalizeGlobal(String value) {
        if (value != null && value.trim().equalsIgnoreCase(GLOBAL_LOVEESSENTIAL)) {
            return GLOBAL_LOVEESSENTIAL;
        }
        return GLOBAL_EXTERNAL;
    }

    /** Выбрано ли в общем значении значение «следовать общему выбору»: пусто или {@code global}. */
    public static boolean followsGlobal(String configured) {
        return configured == null || configured.isBlank() || configured.trim().equalsIgnoreCase(FOLLOW_GLOBAL);
    }

    /**
     * Идентификатор провайдера для плагина.
     *
     * @param kind       вид интеграции
     * @param configured значение из конфига плагина: {@code global}, пусто или явный идентификатор
     *                   ({@code citizens}, {@code cmi}, {@code reviveme}, {@code loveessential})
     * @param global     общий выбор ядра, см. {@link #normalizeGlobal}
     * @return явный идентификатор из конфига плагина в нижнем регистре либо провайдер, который задаёт общий выбор
     */
    public static String resolve(ProviderKind kind, String configured, String global) {
        if (followsGlobal(configured)) {
            return GLOBAL_LOVEESSENTIAL.equals(normalizeGlobal(global)) ? GLOBAL_LOVEESSENTIAL : kind.externalId();
        }
        return configured.trim().toLowerCase(Locale.ROOT);
    }
}
