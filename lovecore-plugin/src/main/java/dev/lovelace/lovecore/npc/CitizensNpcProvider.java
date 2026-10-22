package dev.lovelace.lovecore.npc;

import dev.lovelace.lovecore.api.npc.NpcClick;
import dev.lovelace.lovecore.api.npc.NpcClickEvent;
import dev.lovelace.lovecore.api.npc.NpcHandle;
import dev.lovelace.lovecore.api.npc.NpcProvider;
import dev.lovelace.lovecore.api.npc.NpcSpawnEvent;
import dev.lovelace.lovecore.api.npc.NpcSpec;
import dev.lovelace.lovecore.api.presence.SkinTexture;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.HologramTrait;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.SkinTrait;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@link NpcProvider} поверх Citizens. Все прямые вызовы Citizens, которые раньше были размазаны по
 * плагинам экосистемы (SkinTrait, LookClose, HologramTrait, постоянные метки), собраны здесь.
 *
 * <p>Класс ссылается на типы Citizens, поэтому создавать его можно только когда Citizens включён;
 * ядро проверяет это перед {@code new}. Ключ NPC — его числовой id Citizens, записанный строкой.</p>
 */
public final class CitizensNpcProvider implements NpcProvider, Listener {

    public static final String ID = "citizens";
    private static final String TAG_PREFIX = "lovecore_tag_";
    /** Citizens понимает цвета в виде §-кодов, в том числе hex через §x§r§r§g§g§b§b. */
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private final Plugin owner;

    public CitizensNpcProvider(Plugin owner) {
        this.owner = owner;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, owner);
    }

    @Override
    public String providerId() {
        return ID;
    }

    @Override
    public Optional<NpcHandle> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(CitizensAPI.getNPCRegistry().getById(Integer.parseInt(key.trim()))).map(Handle::new);
        } catch (NumberFormatException notAnId) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<NpcHandle> byEntity(Entity entity) {
        if (entity == null || !CitizensAPI.getNPCRegistry().isNPC(entity)) {
            return Optional.empty();
        }
        return Optional.ofNullable(CitizensAPI.getNPCRegistry().getNPC(entity)).map(Handle::new);
    }

    @Override
    public Optional<NpcHandle> targeted(Player player) {
        NPC selected = CitizensAPI.getDefaultNPCSelector().getSelected(player);
        if (selected != null) {
            return Optional.of(new Handle(selected));
        }
        return byEntity(player.getTargetEntity(10));
    }

    @Override
    public NpcHandle create(NpcSpec spec) {
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(spec.type(), LEGACY.serialize(spec.name()));
        Handle handle = new Handle(npc);
        if (spec.skin() != null) {
            handle.setSkin(spec.skin());
        } else if (spec.skinName() != null) {
            handle.setSkin(spec.skinName());
        }
        handle.lookClose(spec.lookClose());
        handle.hologram(spec.hologram());
        spec.tags().forEach(handle::tag);
        npc.spawn(spec.location());
        return handle;
    }

    @Override
    public boolean remove(String key) {
        Optional<NpcHandle> found = byKey(key);
        found.ifPresent(handle -> ((Handle) handle).npc.destroy());
        return found.isPresent();
    }

    @Override
    public Collection<NpcHandle> all() {
        List<NpcHandle> result = new ArrayList<>();
        for (NPC npc : CitizensAPI.getNPCRegistry().sorted()) {
            result.add(new Handle(npc));
        }
        return result;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRightClick(NPCRightClickEvent event) {
        relay(event, event.getClicker(), event.getNPC(), NpcClick.RIGHT);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeftClick(NPCLeftClickEvent event) {
        relay(event, event.getClicker(), event.getNPC(), NpcClick.LEFT);
    }

    private void relay(org.bukkit.event.Cancellable source, Player clicker, NPC npc, NpcClick click) {
        NpcClickEvent event = new NpcClickEvent(clicker, new Handle(npc), click);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            source.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSpawn(NPCSpawnEvent event) {
        NpcSpawnEvent relayed = new NpcSpawnEvent(new Handle(event.getNPC()));
        Bukkit.getPluginManager().callEvent(relayed);
        if (relayed.isCancelled()) {
            event.setCancelled(true);
        }
    }

    private final class Handle implements NpcHandle {

        private final NPC npc;

        private Handle(NPC npc) {
            this.npc = npc;
        }

        @Override
        public String providerId() {
            return ID;
        }

        @Override
        public String key() {
            return Integer.toString(npc.getId());
        }

        @Override
        public String name() {
            return PlainTextComponentSerializer.plainText().serialize(LEGACY.deserialize(npc.getName()));
        }

        @Override
        public Optional<Entity> entity() {
            return Optional.ofNullable(npc.getEntity());
        }

        @Override
        public boolean spawned() {
            return npc.isSpawned();
        }

        @Override
        public boolean spawn() {
            if (!npc.isSpawned()) {
                Location stored = npc.getStoredLocation();
                if (stored != null) {
                    npc.spawn(stored);
                }
            }
            return npc.isSpawned();
        }

        @Override
        public boolean despawn() {
            boolean wasSpawned = npc.isSpawned();
            npc.despawn();
            return wasSpawned;
        }

        @Override
        public Location location() {
            return npc.isSpawned() ? npc.getEntity().getLocation() : npc.getStoredLocation();
        }

        @Override
        public void setName(Component name) {
            npc.setName(LEGACY.serialize(name));
        }

        @Override
        public Optional<String> skinName() {
            if (!npc.hasTrait(SkinTrait.class)) {
                return Optional.empty();
            }
            String name = npc.getOrAddTrait(SkinTrait.class).getSkinName();
            return name == null || name.isBlank() ? Optional.empty() : Optional.of(name);
        }

        @Override
        public void setSkin(String playerName) {
            npc.getOrAddTrait(SkinTrait.class).setSkinName(playerName);
        }

        @Override
        public void setSkin(SkinTexture texture) {
            npc.getOrAddTrait(SkinTrait.class).setSkinPersistent("lovecore", texture.signature(), texture.value());
        }

        @Override
        public void teleport(Location to) {
            npc.teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }

        @Override
        public void lookClose(boolean enabled) {
            if (enabled) {
                npc.getOrAddTrait(LookClose.class).lookClose(true);
            } else if (npc.hasTrait(LookClose.class)) {
                npc.getOrAddTrait(LookClose.class).lookClose(false);
            }
        }

        @Override
        public void hologram(List<Component> lines) {
            if (lines.isEmpty() && !npc.hasTrait(HologramTrait.class)) {
                return;
            }
            HologramTrait trait = npc.getOrAddTrait(HologramTrait.class);
            trait.clear();
            for (Component line : lines) {
                trait.addLine(LEGACY.serialize(line));
            }
        }

        @Override
        public Optional<String> tag(String key) {
            Object value = npc.data().get(TAG_PREFIX + key);
            return value == null ? Optional.empty() : Optional.of(value.toString());
        }

        @Override
        public void tag(String key, String value) {
            if (value == null) {
                npc.data().remove(TAG_PREFIX + key);
            } else {
                npc.data().setPersistent(TAG_PREFIX + key, value);
            }
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Handle handle && handle.npc.getId() == npc.getId();
        }

        @Override
        public int hashCode() {
            return npc.getId();
        }
    }
}
