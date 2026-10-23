package dev.lovelace.lovecore.api.display;

import java.util.UUID;

/**
 * Личный выбор игрока: показывать ли тег клана и суффикс в чате, в списке игроков (TAB) и над головой.
 *
 * <p>Два направления на каждую пару «поверхность × элемент»: {@link Scope#OWN} — показывать ли
 * МОЙ тег другим, {@link Scope#OTHERS} — показывать ли МНЕ чужие теги. Реализацию держит LoveEssential
 * (там база и меню настроек); чат, список игроков и ник читают флаги отсюда.</p>
 *
 * <p>Значение по умолчанию везде «показано»: пока реализации нет или игрок ничего не менял, сервер
 * выглядит как раньше. Методы чтения безопасно вызывать из любого потока, в том числе из асинхронного
 * потока чата.</p>
 */
public interface DisplayPrefs {

    /** Где показывается подпись. */
    enum Surface {
        /** Сообщение в чате. */
        CHAT,
        /** Запись в списке игроков (TAB). */
        TAB,
        /** Ник над головой. */
        NAMETAG
    }

    /** Что именно скрывается. */
    enum Element {
        /** Тег клана. */
        CLAN_TAG,
        /** Суффикс игрока. */
        SUFFIX
    }

    /** Чьи подписи имеются в виду. */
    enum Scope {
        /** Мои подписи: показывать ли их другим. */
        OWN,
        /** Чужие подписи: показывать ли их мне. */
        OTHERS
    }

    /** Включён ли флаг у игрока; {@code true}, если игрок его не менял. */
    boolean shown(UUID player, Surface surface, Element element, Scope scope);

    /** Задаёт флаг и возвращает прежнее значение. */
    boolean set(UUID player, Surface surface, Element element, Scope scope, boolean shown);

    /** Переключает флаг и возвращает новое значение. */
    default boolean toggle(UUID player, Surface surface, Element element, Scope scope) {
        boolean next = !shown(player, surface, element, scope);
        set(player, surface, element, scope, next);
        return next;
    }

    /**
     * Видит ли {@code viewer} подпись {@code owner} на этой поверхности.
     *
     * <p>Подпись скрыта, если её владелец не хочет её показывать ({@link Scope#OWN}). Свои подписи игрок
     * видит так же, как их видят другие. Чужие подписи дополнительно скрывает флаг зрителя
     * ({@link Scope#OTHERS}).</p>
     */
    default boolean visible(UUID owner, UUID viewer, Surface surface, Element element) {
        if (!shown(owner, surface, element, Scope.OWN)) {
            return false;
        }
        return owner.equals(viewer) || shown(viewer, surface, element, Scope.OTHERS);
    }
}
