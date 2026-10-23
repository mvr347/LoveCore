package dev.lovelace.lovecore.api.provider;

/**
 * Общие настройки интеграций ядра. Плагин-потребитель не читает эту службу сам, а получает ответ через
 * {@link dev.lovelace.lovecore.api.LoveCore#providerId}.
 */
public interface IntegrationSettings {

    /** Общий выбор: {@link ProviderChoice#GLOBAL_LOVEESSENTIAL} или {@link ProviderChoice#GLOBAL_EXTERNAL}. */
    String provider();
}
