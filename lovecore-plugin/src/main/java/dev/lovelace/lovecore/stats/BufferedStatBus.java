package dev.lovelace.lovecore.stats;

import dev.lovelace.lovecore.api.stats.StatBus;
import dev.lovelace.lovecore.api.stats.StatChangedEvent;
import dev.lovelace.lovecore.api.stats.StatSubject;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Шина статистики со склейкой в пределах окна.
 *
 * <p>Зачем склейка. Источники дёргают шину внутри игровых циклов: убийство, тик варки,
 * пересчёт рейтинга. Без буфера сотня событий за секунду превратилась бы в сотню обходов
 * слушателей и сотню записей в базу LoveLeaderboards ради одного и того же итога. В окне
 * прибавки складываются, а точные значения затирают предыдущие — наружу уходит один
 * результат вместо сотни промежуточных.</p>
 *
 * <p>Вызывать можно из любого потока; события всегда доставляются на главном.</p>
 */
public final class BufferedStatBus implements StatBus {

    /** Ключ склейки: одна метрика одного субъекта. */
    private record Key(StatSubject subject, String metric) {}

    /** Накопленное за окно: либо суммарная прибавка, либо точное значение. */
    private static final class Pending {
        private StatChangedEvent.Mode mode;
        private double value;

        Pending(StatChangedEvent.Mode mode, double value) {
            this.mode = mode;
            this.value = value;
        }

        void add(double delta) {
            // Прибавка поверх уже заданного точного значения остаётся точным значением:
            // «стало 10, потом ещё +2» — это 12, а не отдельная прибавка на 2.
            value += delta;
        }

        void set(double newValue) {
            mode = StatChangedEvent.Mode.SET;
            value = newValue;
        }
    }

    private final Plugin plugin;
    private long flushIntervalTicks;
    private final Map<Key, Pending> pending = new LinkedHashMap<>();
    private BukkitTask flushTask;

    public BufferedStatBus(Plugin plugin) {
        this.plugin = plugin;
        // 0 отключает склейку: каждое изменение уходит слушателям сразу.
        this.flushIntervalTicks = Math.max(0L, plugin.getConfig().getLong("stats.flush-interval-ticks", 20L));
    }

    public void start() {
        if (flushIntervalTicks <= 0) {
            plugin.getLogger().info("Статистика: склейка отключена, события уходят сразу.");
            return;
        }
        flushTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flush, flushIntervalTicks, flushIntervalTicks);
    }

    public void shutdown() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        // Досылаем накопленное: иначе последние секунды перед остановкой сервера
        // не доедут до топов.
        flush();
    }

    /** Re-reads {@code stats.flush-interval-ticks} and restarts the flush task on the new period,
     *  flushing whatever was pending under the old one first so nothing is lost in the gap. */
    public void reload() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        flush();
        this.flushIntervalTicks = Math.max(0L, plugin.getConfig().getLong("stats.flush-interval-ticks", 20L));
        start();
    }

    @Override
    public void record(UUID playerId, String metric, double delta) {
        submit(StatSubject.player(playerId), metric, delta, StatChangedEvent.Mode.ADD);
    }

    @Override
    public void set(UUID playerId, String metric, double value) {
        submit(StatSubject.player(playerId), metric, value, StatChangedEvent.Mode.SET);
    }

    @Override
    public void setClan(UUID clanId, String metric, double value) {
        submit(StatSubject.clan(clanId), metric, value, StatChangedEvent.Mode.SET);
    }

    @Override
    public void recordClan(UUID clanId, String metric, double delta) {
        submit(StatSubject.clan(clanId), metric, delta, StatChangedEvent.Mode.ADD);
    }

    private void submit(StatSubject subject, String metric, double value, StatChangedEvent.Mode mode) {
        if (subject == null || metric == null || metric.isBlank()) {
            return;
        }
        if (flushIntervalTicks <= 0) {
            deliver(new StatChangedEvent(subject, metric, value, mode));
            return;
        }
        Key key = new Key(subject, metric);
        synchronized (pending) {
            Pending current = pending.get(key);
            if (current == null) {
                pending.put(key, new Pending(mode, value));
            } else if (mode == StatChangedEvent.Mode.SET) {
                current.set(value);
            } else {
                current.add(value);
            }
        }
    }

    private void flush() {
        List<StatChangedEvent> events;
        synchronized (pending) {
            if (pending.isEmpty()) {
                return;
            }
            events = new ArrayList<>(pending.size());
            pending.forEach((key, value) ->
                    events.add(new StatChangedEvent(key.subject(), key.metric(), value.value, value.mode)));
            pending.clear();
        }
        events.forEach(this::deliver);
    }

    /** Доставка слушателям всегда на главном потоке — им незачем самим туда возвращаться. */
    private void deliver(StatChangedEvent event) {
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getPluginManager().callEvent(event);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getPluginManager().callEvent(event));
        }
    }
}
