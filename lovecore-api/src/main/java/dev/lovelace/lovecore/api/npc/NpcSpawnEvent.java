package dev.lovelace.lovecore.api.npc;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * NPC собирается появиться в мире. Отмена оставляет его незаспавненным — так плагин может держать
 * NPC по расписанию (рынок открыт только днём) независимо от провайдера.
 */
public final class NpcSpawnEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final NpcHandle npc;
    private boolean cancelled;

    public NpcSpawnEvent(NpcHandle npc) {
        this.npc = npc;
    }

    public NpcHandle npc() {
        return npc;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
