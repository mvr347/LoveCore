package dev.lovelace.lovecore.alias;

import dev.lovelace.lovecore.LoveCorePlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Читает/пишет {@code aliases.yml} — единственный источник правды для алиасов LoveCore.
 * И {@code /lovealias}, и ручное редактирование файла (плюс {@code /lovealias reload}) ведут
 * сюда; в обоих случаях после изменения вызывающая сторона обязана дёрнуть
 * {@link AliasManager#applyAll()} (или {@code applyOne}/{@code removeOne}) для немедленного
 * (hot) применения без рестарта сервера.
 */
public class AliasRegistry {

    private final LoveCorePlugin plugin;
    private final File file;
    private final Map<String, AliasDefinition> aliases = new LinkedHashMap<>();

    public AliasRegistry(LoveCorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "aliases.yml");
    }

    public void load() {
        if (!file.exists()) {
            plugin.saveResource("aliases.yml", false);
        }
        aliases.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("aliases");
        if (section == null) return;

        for (String rawName : section.getKeys(false)) {
            ConfigurationSection def = section.getConfigurationSection(rawName);
            if (def == null) continue;
            String name = rawName.toLowerCase(Locale.ROOT);

            String target = def.getString("target");
            if (target == null || target.isBlank()) {
                plugin.getLogger().warning("aliases.yml: алиас '" + name + "' пропущен — не задан target.");
                continue;
            }

            String permission = def.getString("permission");
            if (permission != null && (permission.isBlank() || permission.equalsIgnoreCase("none"))) {
                permission = null;
            }

            AliasDefinition.TabMode tabMode;
            try {
                tabMode = AliasDefinition.TabMode.valueOf(def.getString("tab", "delegate").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("aliases.yml: алиас '" + name + "' — неизвестный tab-режим '"
                        + def.getString("tab") + "', используется delegate.");
                tabMode = AliasDefinition.TabMode.DELEGATE;
            }
            List<String> tabFixed = def.getStringList("tab-suggestions");

            aliases.put(name, new AliasDefinition(name, target, permission, tabMode, tabFixed));
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (AliasDefinition def : aliases.values()) {
            String path = "aliases." + def.name();
            yaml.set(path + ".target", def.target());
            yaml.set(path + ".permission", def.permission());
            yaml.set(path + ".tab", def.tabMode().name().toLowerCase(Locale.ROOT));
            if (!def.tabFixed().isEmpty()) {
                yaml.set(path + ".tab-suggestions", def.tabFixed());
            }
        }
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить aliases.yml: " + e.getMessage());
        }
    }

    public Map<String, AliasDefinition> all() {
        return aliases;
    }

    public AliasDefinition get(String name) {
        return aliases.get(name.toLowerCase(Locale.ROOT));
    }

    public boolean contains(String name) {
        return aliases.containsKey(name.toLowerCase(Locale.ROOT));
    }

    /** Добавляет/заменяет и сразу сохраняет на диск. Применение в CommandMap — забота вызывающей стороны. */
    public void put(AliasDefinition def) {
        aliases.put(def.name(), def);
        save();
    }

    public boolean remove(String name) {
        boolean removed = aliases.remove(name.toLowerCase(Locale.ROOT)) != null;
        if (removed) save();
        return removed;
    }
}
