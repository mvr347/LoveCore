package dev.lovelace.lovecore.api.achievements;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Игрок впервые получил ачивку. Нужен, чтобы другие плагины могли отреагировать (например, выдать награду или
 * показать оформление), не зная, откуда ачивка пришла. Срабатывает на главном потоке.
 */
public class AchievementUnlockedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String achievementId;
    private final AchievementRarity rarity;

    public AchievementUnlockedEvent(Player player, String achievementId, AchievementRarity rarity) {
        this.player = player;
        this.achievementId = achievementId;
        this.rarity = rarity;
    }

    public Player player() {
        return player;
    }

    public String achievementId() {
        return achievementId;
    }

    public AchievementRarity rarity() {
        return rarity;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
