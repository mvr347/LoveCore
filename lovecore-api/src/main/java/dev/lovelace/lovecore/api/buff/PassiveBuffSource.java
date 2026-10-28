package dev.lovelace.lovecore.api.buff;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.Collection;
import java.util.Map;

/**
 * Источник постоянных (пассивных) бонусов игроку. Плагин, у которого есть такая роль
 * (охотник, ветеран, подписчик), регистрирует реализацию в {@code ServicesManager};
 * применяет и снимает бонусы LoveAdaptation, поэтому источнику не нужно самому держать
 * задачи обновления эффектов.
 *
 * <p>Методы вызываются на главном потоке раз в секунду для каждого онлайн-игрока и обязаны быть
 * дешёвыми (чтение кэша, без запросов в БД). Пустой ответ означает «бонуса нет» — прежние
 * эффекты и модификаторы этого источника будут сняты.</p>
 */
public interface PassiveBuffSource {

    /** Стабильный идентификатор источника: часть ключа модификаторов атрибутов. */
    String id();

    /** Зелья, которые игрок должен иметь сейчас (длительность не важна — их обновляют). */
    Collection<PotionEffect> effects(Player player);

    /**
     * Прибавка к базовому значению атрибута, например {@code MAX_HEALTH -> 2.0} (одно сердце).
     */
    Map<Attribute, Double> attributes(Player player);
}
