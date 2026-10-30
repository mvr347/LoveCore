package dev.lovelace.lovecore.api.secrets;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Игрок впервые нашёл тайну. Нужен, чтобы другие плагины могли выдать награду (например, титул),
 * не зная, где именно тайна спрятана.
 */
public class SecretDiscoveredEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String secretId;

    public SecretDiscoveredEvent(Player player, String secretId) {
        this.player = player;
        this.secretId = secretId;
    }

    public Player player() {
        return player;
    }

    public String secretId() {
        return secretId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
