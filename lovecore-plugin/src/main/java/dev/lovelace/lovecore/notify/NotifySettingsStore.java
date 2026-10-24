package dev.lovelace.lovecore.notify;

import dev.lovelace.lovecore.api.notify.LoveNotify.Channel;
import dev.lovelace.lovecore.storage.CoreDatabase;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Персистентное хранилище переключателей LoveNotify (title/actionbar/chat на игрока) в SQLite
 * ({@code lovecore.db}, таблица {@code notify_settings}). Раньше это был плоский YAML-файл.
 *
 * <p>Все значения держатся в памяти (PlaceholderAPI читает их с любого потока), а запись идёт
 * построчно одним фоновым потоком. Если базу открыть не удалось, ядро работает на значениях по
 * умолчанию в памяти и один раз пишет об этом в лог. Старый {@code notify-settings.yml} при первом
 * запуске переносится в базу и переименовывается в {@code .migrated}.</p>
 *
 * <p>Это настройки игрока, а не прогресс: {@code /lovecoreadmin cleardata} их не стирает.</p>
 */
public final class NotifySettingsStore {

    // Built-in default when config key notify.default-channels is absent or has no valid entry.
    // 2026-10-14: owner decision - events go to the action bar by default; chat only gets
    // overflow/important messages (the guaranteed-fallback rule in LoveNotify).
    static final Set<Channel> BUILT_IN_DEFAULT_CHANNELS = EnumSet.of(Channel.ACTION_BAR);

    private final Set<Channel> defaultChannels;

    private final Logger log;
    private final File dataFolder;
    private final ConcurrentHashMap<UUID, EnumSet<Channel>> settings = new ConcurrentHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "LoveCore-settings");
        thread.setDaemon(true);
        return thread;
    });
    private volatile CoreDatabase database;

    public NotifySettingsStore(JavaPlugin plugin) {
        this(plugin.getDataFolder(),
                parseDefaultChannels(plugin.getConfig().getStringList("notify.default-channels"),
                        plugin.getLogger()::warning),
                plugin.getLogger());
    }

    NotifySettingsStore(File dataFolder, Set<Channel> defaultChannels, Logger log) {
        this.dataFolder = dataFolder;
        this.defaultChannels = defaultChannels;
        this.log = log;
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
        try {
            database = CoreDatabase.open(new File(dataFolder, "lovecore.db"));
            importLegacyYaml();
            readAll();
        } catch (SQLException | LinkageError e) {
            // LinkageError: the driver or its native library cannot be loaded; the core must still start.
            database = null;
            log.warning("Не удалось открыть lovecore.db, настройки уведомлений будут жить только в памяти: " + e.getMessage());
        }
    }

    private void readAll() throws SQLException {
        try (PreparedStatement statement = database.connection()
                .prepareStatement("SELECT uuid, channel, enabled FROM notify_settings");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                try {
                    UUID uuid = UUID.fromString(rows.getString(1));
                    Channel channel = Channel.valueOf(rows.getString(2));
                    EnumSet<Channel> enabled = settings.computeIfAbsent(uuid, k -> EnumSet.copyOf(defaultChannels));
                    if (rows.getInt(3) != 0) {
                        enabled.add(channel);
                    } else {
                        enabled.remove(channel);
                    }
                } catch (IllegalArgumentException e) {
                    log.warning("Некорректная строка в notify_settings: " + rows.getString(1) + " / " + rows.getString(2));
                }
            }
        }
    }

    /** One-time move of notify-settings.yml into the table; the file is kept as {@code .migrated}. */
    private void importLegacyYaml() throws SQLException {
        File legacy = new File(dataFolder, "notify-settings.yml");
        if (!legacy.exists()) {
            return;
        }
        var connection = database.connection();
        try (var count = connection.createStatement(); var rows = count.executeQuery("SELECT COUNT(*) FROM notify_settings")) {
            if (rows.next() && rows.getInt(1) > 0) {
                return;
            }
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacy);
        connection.setAutoCommit(false);
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT OR REPLACE INTO notify_settings (uuid, channel, enabled) VALUES (?, ?, ?)")) {
            for (String key : yaml.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    for (Channel channel : Channel.values()) {
                        boolean on = yaml.getBoolean(key + "." + channel.name(), defaultChannels.contains(channel));
                        insert.setString(1, uuid.toString());
                        insert.setString(2, channel.name());
                        insert.setInt(3, on ? 1 : 0);
                        insert.addBatch();
                    }
                } catch (IllegalArgumentException e) {
                    log.warning("Некорректный UUID в notify-settings.yml: " + key);
                }
            }
            insert.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
        if (!legacy.renameTo(new File(dataFolder, "notify-settings.yml.migrated"))) {
            log.warning("notify-settings.yml перенесён в базу, но не переименован: удалите его вручную.");
        }
        log.info("Настройки уведомлений перенесены из notify-settings.yml в lovecore.db.");
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
        EnumSet<Channel> snapshot;
        synchronized (current) {
            if (!enabled && current.size() == 1 && current.contains(channel)) {
                return false;
            }
            if (enabled) {
                current.add(channel);
            } else {
                current.remove(channel);
            }
            snapshot = EnumSet.copyOf(current);
        }
        writer.execute(() -> write(uuid, snapshot));
        return true;
    }

    private void write(UUID uuid, Set<Channel> enabled) {
        CoreDatabase db = database;
        if (db == null) {
            return;
        }
        try (PreparedStatement upsert = db.connection().prepareStatement(
                "INSERT OR REPLACE INTO notify_settings (uuid, channel, enabled) VALUES (?, ?, ?)")) {
            for (Channel channel : Channel.values()) {
                upsert.setString(1, uuid.toString());
                upsert.setString(2, channel.name());
                upsert.setInt(3, enabled.contains(channel) ? 1 : 0);
                upsert.addBatch();
            }
            upsert.executeBatch();
        } catch (SQLException e) {
            log.warning("Не удалось сохранить настройки уведомлений: " + e.getMessage());
        }
    }

    /**
     * Дожидается записей в очереди и закрывает базу; вызывается из {@code onDisable}, чтобы
     * последний тумблер игрока перед остановкой не потерялся.
     */
    public void flush() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warning("Очередь записи настроек уведомлений не опустела за 5 секунд.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        CoreDatabase db = database;
        database = null;
        if (db != null) {
            db.close();
        }
    }
}
