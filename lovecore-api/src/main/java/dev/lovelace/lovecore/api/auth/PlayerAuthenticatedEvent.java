package dev.lovelace.lovecore.api.auth;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Игрок только что успешно вошёл (пароль/Discord/восстановленная сессия/premium-skip).
 *
 * <p>Плагины, которые до этого узнавали момент входа игрока через {@code PlayerJoinEvent},
 * должны переключиться на это событие для любой логики, которую нельзя запускать раньше
 * времени — до этого момента игрок ещё сидит в лимбе LoveAuth и не должен считаться реальным
 * участником игры. Событие однонаправленное: выход/разлогин оно не покрывает — для очистки
 * состояния по-прежнему используйте {@code PlayerQuitEvent}.</p>
 */
public class PlayerAuthenticatedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;

    public PlayerAuthenticatedEvent(Player player) {
        this.player = player;
    }

    public Player player() {
        return player;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
