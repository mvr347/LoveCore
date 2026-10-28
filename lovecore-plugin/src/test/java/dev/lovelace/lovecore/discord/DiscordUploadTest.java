package dev.lovelace.lovecore.discord;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DiscordUploadTest {
  @Test
  void uploadIsBoundedAndDoesNotAllowHeaderInjection() {
    assertThrows(
        IllegalArgumentException.class,
        () -> DiscordUpload.image("text", "file.png", "image/png", new byte[700001]));
    assertThrows(
        IllegalArgumentException.class,
        () -> DiscordUpload.image("text", "file.html", "text/html", new byte[10]));
    var body = DiscordUpload.image("@everyone", "test\r\nEvil: yes.png", "image/png", new byte[10]);
    var text = new String(body.bytes(), StandardCharsets.UTF_8);
    assertFalse(text.contains("\r\nEvil:"));
    assertTrue(text.contains("allowed_mentions"));
    assertTrue(text.contains("files[0]"));
    assertTrue(body.contentType().startsWith("multipart/form-data; boundary="));
  }
}
