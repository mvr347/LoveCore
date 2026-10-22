package dev.lovelace.lovecore.api.npc;

import dev.lovelace.lovecore.api.provider.Named;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;

/**
 * Источник NPC: Citizens или LoveEssential. Плагины-потребители выбирают его строкой в конфиге
 * ({@code integrations.npc-provider}) и находят через
 * {@link dev.lovelace.lovecore.api.LoveCore#provider}.
 *
 * <p>Клики по NPC и спавн приходят общими событиями {@link NpcClickEvent} и {@link NpcSpawnEvent}
 * независимо от провайдера.</p>
 */
public interface NpcProvider extends Named {

    /** NPC по ключу; пусто, если такого нет. */
    Optional<NpcHandle> byKey(String key);

    /** NPC, чьё тело — эта сущность. */
    Optional<NpcHandle> byEntity(Entity entity);

    /** NPC, выбранный игроком командой или находящийся у него под прицелом. */
    Optional<NpcHandle> targeted(Player player);

    NpcHandle create(NpcSpec spec);

    /** Удаляет NPC; {@code false}, если такого не было. */
    boolean remove(String key);

    Collection<NpcHandle> all();
}
