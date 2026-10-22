package dev.lovelace.lovecore.api.npc;

import dev.lovelace.lovecore.api.presence.SkinTexture;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.List;
import java.util.Optional;

/**
 * Один NPC, как его видит плагин-потребитель. Скрывает, чей это NPC — Citizens или LoveEssential.
 * Все методы вызываются с главного потока.
 */
public interface NpcHandle {

    /** Идентификатор провайдера, которому принадлежит NPC. */
    String providerId();

    /**
     * Ключ NPC внутри провайдера. Строка, потому что у Citizens это число, а у LoveEssential — имя;
     * потребитель хранит ключ в конфиге или БД как текст и не гадает о его виде.
     */
    String key();

    /** Имя NPC открытым текстом (без оформления). */
    String name();

    /** Живая сущность, если NPC сейчас в мире. */
    Optional<Entity> entity();

    /** Заспавнен ли NPC. */
    boolean spawned();

    /** Где NPC стоит (или стоял бы, если он выгружен). */
    Location location();

    void setName(Component name);

    /** Показать скин игрока с этим ником. */
    void setSkin(String playerName);

    /** Показать готовую подписанную текстуру. */
    void setSkin(SkinTexture texture);

    void teleport(Location to);

    void lookClose(boolean enabled);

    /** Заменяет строки над NPC; пустой список убирает голограмму. */
    void hologram(List<Component> lines);

    /** Постоянная метка плагина-владельца. */
    Optional<String> tag(String key);

    /** Записывает постоянную метку; {@code null} удаляет её. */
    void tag(String key, String value);
}
