package dev.lovelace.lovecore.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class IntegrationSettingsImplTest {

    private static String load(String yaml) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        var settings = new IntegrationSettingsImpl(Logger.getAnonymousLogger());
        settings.load(config);
        return settings.provider();
    }

    @Test
    void defaultsToExternalWhenTheKeyIsMissing() throws Exception {
        assertEquals("external", load(""));
    }

    @Test
    void readsLoveEssentialCaseInsensitively() throws Exception {
        assertEquals("loveessential", load("integrations:\n  provider: LoveEssential\n"));
    }

    @Test
    void aTypoStaysExternalInsteadOfSwitchingProviders() throws Exception {
        assertEquals("external", load("integrations:\n  provider: loveessentail\n"));
    }

    @Test
    void reloadPicksUpTheNewValue() throws Exception {
        var settings = new IntegrationSettingsImpl(Logger.getAnonymousLogger());
        var config = new YamlConfiguration();
        config.loadFromString("integrations:\n  provider: loveessential\n");
        settings.load(config);
        assertEquals("loveessential", settings.provider());
        config.loadFromString("integrations:\n  provider: external\n");
        settings.load(config);
        assertEquals("external", settings.provider());
    }
}
