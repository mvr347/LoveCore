package dev.lovelace.lovecore.gui;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GuiStylesTest {
    @TempDir Path root;
    private YamlConfiguration manifest() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("version: 20261101\nupdates:\n- file: gui.yml\n  path: icon\n  value: new-texture\n");
        return yaml;
    }
    @Test void backsUpAndPreservesUnrelatedTemplatesAndLaterCustomization() throws Exception {
        String original = "icon: old\nplaceholders:\n  test: '&7%clans_tag% <gradient:red:blue>Текст</gradient>'\n";
        Path file = root.resolve("gui.yml");
        Files.writeString(file, original);
        GuiStyles.migrate(root, manifest());
        assertEquals(original, Files.readString(root.resolve("gui.yml.pre-style-20261101.bak")));
        var result = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals("new-texture", result.getString("icon"));
        assertEquals("&7%clans_tag% <gradient:red:blue>Текст</gradient>", result.getString("placeholders.test"));
        Files.writeString(file, "icon: customized\n");
        GuiStyles.migrate(root, manifest());
        assertEquals("icon: customized\n", Files.readString(file));
    }
    @Test void invalidSecondDocumentLeavesFirstUntouched() throws Exception {
        Files.writeString(root.resolve("gui.yml"), "icon: old\n");
        Files.writeString(root.resolve("broken.yml"), "icon: [\n");
        var m = manifest();
        m.loadFromString(m.saveToString()+"- file: broken.yml\n  path: icon\n  value: new\n");
        assertThrows(Exception.class, () -> GuiStyles.migrate(root, m));
        assertEquals("icon: old\n", Files.readString(root.resolve("gui.yml")));
        assertFalse(Files.exists(root.resolve(".gui-style-20261101")));
    }
    @Test void refusesPathsOutsidePluginDataDirectory() throws Exception {
        var m = manifest();
        m.loadFromString(m.saveToString().replace("gui.yml", "../other.yml"));
        assertThrows(Exception.class, () -> GuiStyles.migrate(root,m));
    }
}
