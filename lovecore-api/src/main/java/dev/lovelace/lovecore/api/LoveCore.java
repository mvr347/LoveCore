package dev.lovelace.lovecore.api;

import dev.lovelace.lovecore.api.display.DisplayPrefs;
import dev.lovelace.lovecore.api.provider.Named;
import dev.lovelace.lovecore.api.provider.Providers;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
        // No server (a unit test, an early static initialiser): there is no core either.
        if (Bukkit.getServer() == null) {
            return Optional.empty();
        }
        RegisteredServiceProvider<T> provider = Bukkit.getServicesManager().getRegistration(type);
        return Optional.ofNullable(provider).map(RegisteredServiceProvider::getProvider);
    }

    /**
     * Реализация контракта с конкретным именем — для контрактов, у которых на сервере несколько
     * реализаций сразу ({@code NpcProvider}, {@code PresenceOracle}, {@code DownedOracle}).
     *
     * <p>Потребителю обычно нужен {@link #loveEssential}: другие провайдеры NPC, присутствия и раненых
     * игроков в экосистеме не поддерживаются. Пустой ответ означает, что провайдер не установлен или ещё
     * не поднялся, — потребитель говорит об этом вслух и работает без интеграции.</p>
     */
    public static <T extends Named> Optional<T> provider(Class<T> type, String providerId) {
        if (Bukkit.getServer() == null) {
            return Optional.empty();
        }
        List<T> candidates = new ArrayList<>();
        for (RegisteredServiceProvider<T> registration : Bukkit.getServicesManager().getRegistrations(type)) {
            candidates.add(registration.getProvider());
        }
        return Providers.choose(candidates, providerId);
    }

    /**
     * Реализация контракта от LoveEssential — единственного провайдера NPC, присутствия игроков и раненых
     * игроков в экосистеме. Пустой ответ означает, что LoveEssential не установлен или ещё не поднялся.
     */
    public static <T extends Named> Optional<T> loveEssential(Class<T> type) {
        return provider(type, Providers.LOVE_ESSENTIAL);
    }

    /**
     * Видит ли {@code viewer} тег клана или суффикс {@code owner} на этой поверхности по личным настройкам.
     * Без реализации {@link DisplayPrefs} всё показано.
     */
    public static boolean displayVisible(UUID owner, UUID viewer, DisplayPrefs.Surface surface,
                                         DisplayPrefs.Element element) {
        return service(DisplayPrefs.class)
                .map(prefs -> prefs.visible(owner, viewer, surface, element))
                .orElse(true);
    }

    /** Установлено ли ядро. Годится для отчёта об интеграциях на старте. */
    public static boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("LoveCore") != null;
    }
}
