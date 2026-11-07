package dev.lovelace.lovecore.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class BotIdentityTest {
  private static String head(String id) {
    return Base64.getEncoder().withoutPadding().encodeToString(id.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void theIdIsTheDecodedFirstPartOfTheToken() {
    assertEquals("123456789012345678", BotIdentity.userIdFromToken(head("123456789012345678") + ".Gabcde.secretpart"));
    assertEquals("1234567890123456789", BotIdentity.userIdFromToken("  " + head("1234567890123456789") + ".x.y "));
  }

  @Test
  void aBlankOrBrokenTokenGivesNoId() {
    assertNull(BotIdentity.userIdFromToken(null));
    assertNull(BotIdentity.userIdFromToken(""));
    assertNull(BotIdentity.userIdFromToken("   "));
    assertNull(BotIdentity.userIdFromToken("notatoken"));
    assertNull(BotIdentity.userIdFromToken("!!!.x.y"));
    assertNull(BotIdentity.userIdFromToken(head("not-digits") + ".x.y"));
  }
}
