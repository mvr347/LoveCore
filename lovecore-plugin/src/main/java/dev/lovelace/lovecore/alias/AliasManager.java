package dev.lovelace.lovecore.alias;

import dev.lovelace.lovecore.LoveCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Держит набор реально зарегистрированных в {@link CommandMap} {@link DynamicAliasCommand} и
 * синхронизирует его с {@link AliasRegistry} по требованию — после {@code /lovealias
 * add|remove|reload} или ручной правки {@code aliases.yml} + {@code /lovealias reload}.
 * Использует только официальный {@code Bukkit.getCommandMap()}/{@code CommandMap#register} —
 * без обращения к CraftBukkit и без рефлексии, поэтому одинаково работает на любом форке
 * Paper (Purpur и т.п.), не завязываясь на конкретный пакет реализации сервера.
 */
public class AliasManager {

    private final LoveCorePlugin plugin;
    private final AliasRegistry registry;
    private final Map<String, DynamicAliasCommand> registered = new HashMap<>();
    /** true, если алиас занял голый /<name> слот; false, если из-за конфликта имени CommandMap отступил на "lovecore:<name>". */
    private final Map<String, Boolean> primarySlot = new HashMap<>();

    public AliasManager(LoveCorePlugin plugin, AliasRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    /** Полная пересинхронизация: снимает всё, что было зарегистрировано этим менеджером, и регистрирует заново из registry. */
    public void applyAll() {
        CommandMap map = Bukkit.getCommandMap();
        for (DynamicAliasCommand command : registered.values()) {
            command.unregister(map);
        }
        registered.clear();
        primarySlot.clear();

        for (AliasDefinition def : registry.all().values()) {
            registerOne(map, def);
        }
    }

    /** Регистрирует/перерегистрирует один алиас, не трогая остальные. */
    public void applyOne(AliasDefinition def) {
        CommandMap map = Bukkit.getCommandMap();
        DynamicAliasCommand existing = registered.remove(def.name());
        if (existing != null) {
            existing.unregister(map);
        }
        primarySlot.remove(def.name());
        registerOne(map, def);
    }

    public void removeOne(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        DynamicAliasCommand existing = registered.remove(key);
        if (existing != null) {
            existing.unregister(Bukkit.getCommandMap());
        }
        primarySlot.remove(key);
    }

    /** true = алиас реально отвечает на голый /<name>; false = отступил на "lovecore:<name>" из-за конфликта имени. */
    public boolean isPrimarySlot(String name) {
        return primarySlot.getOrDefault(name.toLowerCase(Locale.ROOT), false);
    }

    public boolean isRegistered(String name) {
        return registered.containsKey(name.toLowerCase(Locale.ROOT));
    }

    private void registerOne(CommandMap map, AliasDefinition def) {
        DynamicAliasCommand command = new DynamicAliasCommand(def);
        boolean primary = map.register("lovecore", command);
        registered.put(def.name(), command);
        primarySlot.put(def.name(), primary);
        if (!primary) {
            plugin.getLogger().warning("Алиас '" + def.name() + "' конфликтует с уже занятым именем команды - "
                    + "доступен только как /lovecore:" + def.name() + ", а не голым /" + def.name() + ".");
        }
    }

    public int count() {
        return registered.size();
    }
}
