package dev.lovelace.lovecore.notify;

import dev.lovelace.lovecore.api.notify.LoveNotify.Channel;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Персистентное хранилище переключателей LoveNotify (title/actionbar/chat на игрока) —
 * плоский YAML-файл, не SQLite: у ядра сегодня вообще нет базы, а тут всего три булевых
 * значения на игрока, так что заводить SQL-таблицу ради этого было бы избыточно.
 *
 * <p>Весь файл целиком читается в память при старте и пишется обратно (асинхронно) при
 * каждом изменении — при типичном масштабе (сотни-тысячи игроков, редкие тумблеры) это
 * дешевле, чем городить построчный формат.</p>
 */
public final class NotifySettingsStore {

    // Built-in default when config key notify.default-channels is absent or has no valid entry.
    // 2026-10-14: owner decision - events go to the action bar by default; chat only gets
    // overflow/important messages (the guaranteed-fallback rule in LoveNotify).
    static final Set<Channel> BUILT_IN_DEFAULT_CHANNELS = EnumSet.of(Channel.ACTION_BAR);

    private final Set<Channel> defaultChannels;

    private final JavaPlugin plugin;
    private final File file;
    private final ConcurrentHashMap<UUID, EnumSet<Channel>> settings = new ConcurrentHashMap<>();

    public NotifySettingsStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.defaultChannels = parseDefaultChannels(plugin.getConfig().getStringList("notify.default-channels"),
                plugin.getLogger()::warning);
        this.file = new File(plugin.getDataFolder(), "notify-settings.yml");
    }

    /**
     * Parses the configured default channel names. Unknown names are reported and skipped; an empty
     * or fully invalid list falls back to the built-in default (at least one channel must stay on).
     */
    static Set<Channel> parseDefaultChannels(java.util.List<String> names, java.util.function.Consumer<String> warn) {
        EnumSet<Channel> result = EnumSet.noneOf(Channel.class);
        if (names != null) {
            for (String name : names) {
                try {
                    result.add(Channel.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_')));
                } catch (IllegalArgumentException e) {
                    warn.accept("Неизвестный канал в notify.default-channels: " + name);
                }
            }
        }
        return result.isEmpty() ? EnumSet.copyOf(BUILT_IN_DEFAULT_CHANNELS) : result;
    }

    public void load() {
        settings.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                EnumSet<Channel> enabled = EnumSet.noneOf(Channel.class);
                for (Channel channel : Channel.values()) {
                    if (yaml.getBoolean(key + "." + channel.name(), defaultChannels.contains(channel))) {
                        enabled.add(channel);
                    }
                }
                settings.put(uuid, enabled);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Некорректный UUID в notify-settings.yml: " + key);
            }
        }
    }

    public Set<Channel> getEnabledChannels(UUID uuid) {
        EnumSet<Channel> stored = settings.get(uuid);
        if (stored == null) {
            return EnumSet.copyOf(defaultChannels);
        }
        // Same lock object setChannelEnabled mutates under: EnumSet isn't thread-safe, and
        // without this a reader on another thread (e.g. a PlaceholderAPI request, which can be
        // evaluated off the main thread) could race a toggle and see a torn/stale set.
        synchronized (stored) {
            return EnumSet.copyOf(stored);
        }
    }

    /**
     * @return {@code true}, если изменение применено. {@code false} — если это была попытка
     * выключить последний оставшийся включённый канал (отклонено без изменений: минимум
     * один канал должен быть включён всегда, иначе игрок не увидит вообще ничего).
     */
    public boolean setChannelEnabled(UUID uuid, Channel channel, boolean enabled) {
        EnumSet<Channel> current = settings.computeIfAbsent(uuid, k -> EnumSet.copyOf(defaultChannels));
        synchronized (current) {
            if (!enabled && current.size() == 1 && current.contains(channel)) {
                return false;
            }
            if (enabled) {
                current.add(channel);
            } else {
                current.remove(channel);
            }
        }
        saveAsync();
        return true;
    }

    private void saveAsync() {
        Bukkit.getAsyncScheduler().runNow(plugin, task -> writeToDisk());
    }

    /**
     * Синхронный вариант {@link #saveAsync()} для {@code onDisable}: на выключении сервера
     * ждать асинхронную задачу уже негде, а последний тумблер игрока перед остановкой не
     * должен потеряться так же, как {@link dev.lovelace.lovecore.stats.BufferedStatBus}
     * досылает накопленную статистику синхронно перед выходом.
     */
    public void flush() {
        writeToDisk();
    }

    private void writeToDisk() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (var entry : settings.entrySet()) {
            String base = entry.getKey().toString();
            EnumSet<Channel> value = entry.getValue();
            // Тот же замок, что и на записи в setChannelEnabled/getEnabledChannels: EnumSet не
            // потокобезопасен, а сериализация читает его поэлементно.
            synchronized (value) {
                for (Channel channel : Channel.values()) {
                    yaml.set(base + "." + channel.name(), value.contains(channel));
                }
            }
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить notify-settings.yml: " + e.getMessage());
        }
    }
}
