package dev.lovelace.lovecore.api.npc;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Игрок нажал на NPC. Одно и то же событие для любого провайдера: потребителю не нужно слушать
 * {@code NPCRightClickEvent} Citizens и события LoveEssential по отдельности.
 *
 * <p>Событие приходит от всех провайдеров сразу; потребитель сверяет {@code npc().providerId()}
 * со своим конфигом и игнорирует чужие.</p>
 */
public final class NpcClickEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final NpcHandle npc;
    private final NpcClick click;
    private boolean cancelled;

    public NpcClickEvent(Player player, NpcHandle npc, NpcClick click) {
        super(player);
        this.npc = npc;
        this.click = click;
    }

    public NpcHandle npc() {
        return npc;
    }

    public NpcClick click() {
        return click;
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
