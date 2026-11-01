package dev.lovelace.lovecore.gui;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** One-time, backed-up presentation updates. Gameplay values and placeholder catalogs are untouched. */
public final class GuiStyles {
    private GuiStyles() {}

    public static void migrate(JavaPlugin plugin) {
        for (String resource : List.of("gui-style-migration.yml", "gui-style-followup.yml")) {
            try (InputStream stream = plugin.getResource(resource)) {
                if (stream == null) continue;
            var manifest = new YamlConfiguration();
            manifest.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            migrate(plugin.getDataFolder().toPath(), manifest);
        } catch (Exception error) {
            throw new IllegalStateException("Не удалось обновить оформление " + plugin.getName()
                    + "; исходные файлы сохранены", error);
        }
        }
    }

    static void migrate(Path directory, YamlConfiguration manifest) throws Exception {
        Path root = directory.toAbsolutePath().normalize();
        String version = Long.toString(manifest.getLong("version"));
        Path marker = root.resolve(".gui-style-" + version);
        if (Files.exists(marker)) return;
        Map<Path, byte[]> original = new LinkedHashMap<>();
        Map<Path, YamlConfiguration> documents = new LinkedHashMap<>();
        // Parse every document before modifying even the first file.
        for (Map<?, ?> update : manifest.getMapList("updates")) {
            String file = Objects.toString(update.get("file"), "");
            Path path = root.resolve(file).normalize();
            if (!path.startsWith(root) || !file.endsWith(".yml"))
                throw new IOException("Недопустимый путь оформления: " + file);
            if (!Files.exists(path)) continue; // Fresh installs use the bundled defaults.
            if (!documents.containsKey(path)) {
                byte[] bytes = Files.readAllBytes(path);
                var document = new YamlConfiguration();
                document.options().parseComments(true);
                document.loadFromString(new String(bytes, StandardCharsets.UTF_8));
                original.put(path, bytes);
                documents.put(path, document);
            }
            String key = Objects.toString(update.get("path"), "");
            if (key.isBlank()) throw new IOException("Пустой путь настройки оформления");
            var document = documents.get(path);
            // Follow-up manifests can upgrade shipped defaults while retaining an admin's edits.
            if (Boolean.TRUE.equals(update.get("preserve-custom"))
                    && document.contains(key)
                    && !Objects.equals(document.get(key), update.get("before"))) continue;
            document.set(key, update.get("value"));
        }
        Files.createDirectories(root);
        Map<Path, Path> staged = new LinkedHashMap<>();
        List<Path> written = new ArrayList<>();
        try {
            for (var entry : documents.entrySet()) {
                Path path = entry.getKey();
                Path backup = path.resolveSibling(path.getFileName() + ".pre-style-" + version + ".bak");
                if (!Files.exists(backup)) Files.write(backup, original.get(path), StandardOpenOption.CREATE_NEW);
                Path temporary = Files.createTempFile(path.getParent(), ".gui-style-", ".tmp");
                staged.put(path, temporary);
                Files.writeString(temporary, entry.getValue().saveToString(), StandardCharsets.UTF_8);
            }
            for (var entry : staged.entrySet()) {
                replace(entry.getValue(), entry.getKey());
                written.add(entry.getKey());
            }
            Files.writeString(marker, version, StandardOpenOption.CREATE_NEW);
        } catch (Exception error) {
            for (Path path : written) {
                try { Files.write(path, original.get(path)); }
                catch (IOException rollback) { error.addSuppressed(rollback); }
            }
            throw error;
        } finally {
            for (Path temporary : staged.values()) Files.deleteIfExists(temporary);
        }
    }

    private static void replace(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException error) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
