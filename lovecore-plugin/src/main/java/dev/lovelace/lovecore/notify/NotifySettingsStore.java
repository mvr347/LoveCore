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

    // По умолчанию включён только чат — title/actionbar игрок включает сам через
    // /lovenotify toggle, если хочет. Чат никогда не выключается автоматически: см.
    // guaranteed-fallback правило в LoveNotify и минимум-один-канал ниже в setChannelEnabled.
    private static final Set<Channel> DEFAULT_CHANNELS = EnumSet.of(Channel.CHAT);

    private final JavaPlugin plugin;
    private final File file;
    private final ConcurrentHashMap<UUID, EnumSet<Channel>> settings = new ConcurrentHashMap<>();

    public NotifySettingsStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "notify-settings.yml");
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
                    if (yaml.getBoolean(key + "." + channel.name(), DEFAULT_CHANNELS.contains(channel))) {
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
        return stored != null ? EnumSet.copyOf(stored) : EnumSet.copyOf(DEFAULT_CHANNELS);
    }

    /**
     * @return {@code true}, если изменение применено. {@code false} — если это была попытка
     * выключить последний оставшийся включённый канал (отклонено без изменений: минимум
     * один канал должен быть включён всегда, иначе игрок не увидит вообще ничего).
     */
    public boolean setChannelEnabled(UUID uuid, Channel channel, boolean enabled) {
        EnumSet<Channel> current = settings.computeIfAbsent(uuid, k -> EnumSet.copyOf(DEFAULT_CHANNELS));
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
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            YamlConfiguration yaml = new YamlConfiguration();
            for (var entry : settings.entrySet()) {
                String base = entry.getKey().toString();
                for (Channel channel : Channel.values()) {
                    yaml.set(base + "." + channel.name(), entry.getValue().contains(channel));
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
        });
    }
}
