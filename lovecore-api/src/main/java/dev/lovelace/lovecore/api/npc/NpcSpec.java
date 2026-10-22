package dev.lovelace.lovecore.api.npc;

import dev.lovelace.lovecore.api.presence.SkinTexture;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Описание NPC, которого просит создать плагин-потребитель. Не зависит от провайдера: один и тот же
 * набор полей превращается и в NPC Citizens, и в NPC LoveEssential.
 *
 * @param name      имя над головой
 * @param location  где появиться
 * @param type      тип сущности; {@code PLAYER} даёт NPC с подставляемым скином
 * @param skinName  ник, чей скин показывать, или {@code null}
 * @param skin      готовая подписанная текстура или {@code null}; приоритетнее {@code skinName}
 * @param lookClose поворачиваться ли к ближайшему игроку
 * @param hologram  строки над NPC; пусто — без голограммы
 * @param tags      постоянные метки плагина-владельца: переживают рестарт и позволяют опознать
 *                  «своего» NPC без разбора имени
 */
public record NpcSpec(
        Component name,
        Location location,
        EntityType type,
        String skinName,
        SkinTexture skin,
        boolean lookClose,
        List<Component> hologram,
        Map<String, String> tags) {

    public NpcSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        type = type == null ? EntityType.PLAYER : type;
        hologram = hologram == null ? List.of() : List.copyOf(hologram);
        tags = tags == null ? Map.of() : Map.copyOf(tags);
    }

    /** Игрок-NPC с именем в заданной точке: остальное по умолчанию. */
    public static NpcSpec at(Component name, Location location) {
        return new NpcSpec(name, location, EntityType.PLAYER, null, null, false, List.of(), Map.of());
    }

    public NpcSpec withSkinName(String skinName) {
        return new NpcSpec(name, location, type, skinName, skin, lookClose, hologram, tags);
    }

    public NpcSpec withSkin(SkinTexture skin) {
        return new NpcSpec(name, location, type, skinName, skin, lookClose, hologram, tags);
    }

    public NpcSpec withLookClose(boolean lookClose) {
        return new NpcSpec(name, location, type, skinName, skin, lookClose, hologram, tags);
    }

    public NpcSpec withHologram(List<Component> hologram) {
        return new NpcSpec(name, location, type, skinName, skin, lookClose, hologram, tags);
    }

    public NpcSpec withTags(Map<String, String> tags) {
        return new NpcSpec(name, location, type, skinName, skin, lookClose, hologram, tags);
    }

    public NpcSpec withType(EntityType type) {
        return new NpcSpec(name, location, type, skinName, skin, lookClose, hologram, tags);
    }
}
