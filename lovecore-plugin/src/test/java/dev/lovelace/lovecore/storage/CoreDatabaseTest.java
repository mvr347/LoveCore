package dev.lovelace.lovecore.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoreDatabaseTest {
    @TempDir File dir;

    @Test
    void opensCreatesTheTableAndKeepsRows() throws Exception {
        File file = new File(dir, "lovecore.db");
        try (var db = CoreDatabase.open(file);
             var insert = db.connection().prepareStatement("INSERT INTO notify_settings VALUES ('u', 'CHAT', 1)")) {
            insert.executeUpdate();
        }
        try (var db = CoreDatabase.open(file);
             var rows = db.connection().createStatement().executeQuery("SELECT enabled FROM notify_settings")) {
            assertTrue(rows.next());
            assertEquals(1, rows.getInt(1));
        }
    }
}
