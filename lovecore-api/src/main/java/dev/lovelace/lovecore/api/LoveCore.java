package dev.lovelace.lovecore.api;

import dev.lovelace.lovecore.api.provider.Named;
import dev.lovelace.lovecore.api.provider.Providers;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Единственный способ обращения к ядру из соседних плагинов.
 *
 * <p>Возвращает {@link Optional} намеренно: плагин обязан работать и без ядра, как сегодня
 * работает через {@code softdepend}. Отсутствие ядра — это не ошибка, а штатный случай, в
 * котором вызывающий падает на прежнее поведение:</p>
 *
 * <pre>{@code
 * return LoveCore.service(CombatState.class)
 *         .map(state -> state.inCombat(player.getUniqueId()))
 *         // Ядра нет — падаем на прежнюю проверку метки, а не отключаем защиту молча.
 *         .orElseGet(() -> hasCombatMetadata(player));
 * }</pre>
 */
public final class LoveCore {

    private LoveCore() {
    }

    /**
     * Служба ядра, если ядро установлено и успело подняться.
     *
     * <p>Безопасно вызывать до включения ядра: пока служба не зарегистрирована, ответ пустой.
     * Кэшировать результат в поле не нужно и вредно — соседний плагин может зарегистрировать
     * более точную реализацию оракула позже, и закэшированная ссылка её не увидит.</p>
     */
    public static <T> Optional<T> service(Class<T> type) {
        RegisteredServiceProvider<T> provider = Bukkit.getServicesManager().getRegistration(type);
        return Optional.ofNullable(provider).map(RegisteredServiceProvider::getProvider);
    }

    /**
     * Реализация контракта с конкретным именем — для контрактов, у которых на сервере несколько
     * реализаций сразу ({@code NpcProvider}, {@code PresenceOracle}, {@code DownedOracle}).
     *
     * <p>Идентификатор берётся из конфига потребителя ({@code citizens}, {@code cmi},
     * {@code loveessential}). Пустой ответ означает, что выбранный провайдер не установлен или ещё
     * не поднялся, — потребитель говорит об этом вслух и работает без интеграции.</p>
     */
    public static <T extends Named> Optional<T> provider(Class<T> type, String providerId) {
        List<T> candidates = new ArrayList<>();
        for (RegisteredServiceProvider<T> registration : Bukkit.getServicesManager().getRegistrations(type)) {
            candidates.add(registration.getProvider());
        }
        return Providers.choose(candidates, providerId);
    }

    /** Установлено ли ядро. Годится для отчёта об интеграциях на старте. */
    public static boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("LoveCore") != null;
    }
}
