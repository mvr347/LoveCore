package dev.lovelace.lovecore.discord;

import static org.junit.jupiter.api.Assertions.*;
import dev.lovelace.lovecore.api.discord.DiscordRequestRejectedException;
import org.junit.jupiter.api.Test;

class DiscordMessageResultTest {
  @Test
  void onlyExplicitRefusalsPermitAnotherCreate() {
    for (int status : java.util.List.of(400, 401, 403, 404, 413, 422, 429)) {
      var error =
          assertThrows(
              DiscordRequestRejectedException.class,
              () -> DiscordMessageResult.id(status, "SENSITIVE TOKEN"));
      assertEquals(status, error.getStatusCode());
      assertFalse(error.getMessage().contains("SENSITIVE"));
    }
    for (int status : java.util.List.of(500, 502, 503, 504)) {
      var error =
          assertThrows(
              IllegalStateException.class, () -> DiscordMessageResult.id(status, "SENSITIVE"));
      assertFalse(error instanceof DiscordRequestRejectedException);
    }
  }

  @Test
  void malformedSuccessDoesNotPermitBlindCreate() {
    assertEquals("123456789", DiscordMessageResult.id(200, "{\"id\":\"123456789\"}"));
    assertThrows(RuntimeException.class, () -> DiscordMessageResult.id(200, "{}"));
    assertThrows(RuntimeException.class, () -> DiscordMessageResult.id(200, "{\"id\":\"bad\"}"));
  }
}
