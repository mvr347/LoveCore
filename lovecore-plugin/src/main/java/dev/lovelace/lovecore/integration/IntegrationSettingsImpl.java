package dev.lovelace.lovecore.integration;

import dev.lovelace.lovecore.api.provider.IntegrationSettings;
import dev.lovelace.lovecore.api.provider.ProviderChoice;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.logging.Logger;

/** Общий выбор провайдера из {@code integrations.provider}, который перечитывается при reload ядра. */
public final class IntegrationSettingsImpl implements IntegrationSettings {

    static final String KEY = "integrations.provider";

    private final Logger log;
    private volatile String provider = ProviderChoice.GLOBAL_EXTERNAL;

    public IntegrationSettingsImpl(Logger log) {
        this.log = log;
    }

    public void load(FileConfiguration config) {
        String raw = config.getString(KEY, ProviderChoice.GLOBAL_EXTERNAL);
        String normalized = ProviderChoice.normalizeGlobal(raw);
        // Опечатка не должна молча включать провайдера LoveEssential: привязки NPC у разных провайдеров не совместимы.
        if (raw != null && !raw.isBlank() && !raw.trim().equalsIgnoreCase(normalized)) {
            log.warning(KEY + ": неизвестное значение «" + raw + "», использую «" + normalized
                    + "». Допустимо: " + ProviderChoice.GLOBAL_LOVEESSENTIAL + " или " + ProviderChoice.GLOBAL_EXTERNAL + ".");
        }
        this.provider = normalized;
    }

    @Override
    public String provider() {
        return provider;
    }
}
