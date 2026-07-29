package dev.lovelace.lovecore.api.stats;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Метрика изменилась. Это то, на что подписывается LoveLeaderboards вместо трёх рефлексий
 * в чужие классы.
 *
 * <p>Всегда приходит на главном потоке, даже если {@link StatBus} дёрнули из асинхронной
 * задачи, — слушателю не нужно самому возвращаться в главный поток перед работой с миром.</p>
 */
public class StatChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    /** Как понимать {@link #value()}: прибавка к накопительной метрике или точное значение. */
    public enum Mode { ADD, SET }

    private final StatSubject subject;
    private final String metric;
    private final double value;
    private final Mode mode;

    public StatChangedEvent(StatSubject subject, String metric, double value, Mode mode) {
        this.subject = subject;
        this.metric = metric;
        this.value = value;
        this.mode = mode;
    }

    public StatSubject subject() {
        return subject;
    }

    /** Имя метрики из словаря {@link Metrics}. */
    public String metric() {
        return metric;
    }

    /** Прибавка при {@link Mode#ADD}, точное значение при {@link Mode#SET}. */
    public double value() {
        return value;
    }

    public Mode mode() {
        return mode;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
