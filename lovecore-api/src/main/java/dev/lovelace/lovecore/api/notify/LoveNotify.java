package dev.lovelace.lovecore.api.notify;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

/**
 * Единая точка показа уведомлений игроку — title/actionbar/chat как три независимых канала,
 * которые игрок сам включает и выключает в любой комбинации (см. {@link Channel}).
 *
 * <p>Канал CHAT особый: он не просто "ещё один канал", а гарантированный запасной вариант.
 * {@link #notify} всегда покажет сообщение в чате, если оно помечено как {@code important},
 * слишком длинное для title/actionbar, либо ни один другой канал не сработал (все выключены
 * игроком или не были запрошены вызывающим) — иначе игрок мог бы выключить всё и не увидеть
 * ничего вообще. Обычный чат-тумблер управляет только "необязательными" дублирующими
 * уведомлениями в чате, не этим предохранителем.</p>
 *
 * <p>{@link #notifyInteractive} — отдельный путь для сообщений с кликабельными кнопками
 * (подтвердить/отменить и т.п.): они физически не могут отрендериться в title/actionbar,
 * поэтому всегда идут в чат и не подчиняются переключателям каналов вообще.</p>
 *
 * <p>Минимум один канал должен быть включён всегда — {@link #setChannelEnabled} отклоняет
 * попытку выключить последний оставшийся канал (см. его javadoc), иначе игрок мог бы
 * отключить всё и перестать видеть даже {@code important}-уведомления в реальном времени
 * (запасной канал в {@link #notify} — это отдельная защита на момент отправки, а не замена
 * этому правилу на момент настройки).</p>
 */
public interface LoveNotify {

    enum Channel { CHAT, ACTION_BAR, TITLE }

    /** Включённые у игрока каналы (по умолчанию — только CHAT, до первого /lovenotify toggle). */
    Set<Channel> getEnabledChannels(UUID uuid);

    boolean isChannelEnabled(UUID uuid, Channel channel);

    /**
     * Переключает канал. Если это попытка выключить последний оставшийся включённый канал —
     * игнорируется (см. {@link #setChannelEnabled}), и возвращается прежнее состояние.
     *
     * @return итоговое состояние канала после операции ({@code true} = включён)
     */
    boolean toggleChannel(UUID uuid, Channel channel);

    /**
     * @return {@code true}, если изменение применено. {@code false} — попытка выключить
     * последний оставшийся включённый канал была отклонена, состояние не изменилось.
     */
    boolean setChannelEnabled(UUID uuid, Channel channel, boolean enabled);

    /**
     * Показывает уведомление по запрошенным каналам, отфильтрованным настройками игрока.
     * Чат — гарантированный запасной канал (см. класс-javadoc), остальные два — строго по
     * пересечению {@code requestedChannels} и настроек игрока.
     *
     * @param requestedChannels какие каналы вызывающий код вообще хочет использовать для
     *                          этого конкретного сообщения (не все уведомления уместны в title)
     * @param important         {@code true} — сообщение обязано попасть в чат независимо от
     *                          чат-тумблера игрока (критичная информация)
     */
    void notify(Player player, Component message, Set<Channel> requestedChannels, boolean important);

    /** Короткая форма: все три канала запрошены, не помечено как important. */
    default void notify(Player player, Component message) {
        notify(player, message, Set.of(Channel.CHAT, Channel.ACTION_BAR, Channel.TITLE), false);
    }

    /**
     * Сообщение с интерактивными элементами (кнопки, click-события) — всегда только в чат,
     * не подчиняется переключателям каналов ни при каких обстоятельствах.
     */
    void notifyInteractive(Player player, Component message);
}
