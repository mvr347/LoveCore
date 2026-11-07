package dev.lovelace.lovecore.discord;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** The bot's Discord user id, read from its token (no network call needed). */
final class BotIdentity {

  private BotIdentity() {}

  /**
   * The first dot-separated part of a bot token is the base64 of the bot's user id.
   *
   * @return the id (digits only), or {@code null} when the token is blank or has another shape
   */
  static String userIdFromToken(String token) {
    if (token == null || token.isBlank()) return null;
    String head = token.trim();
    int dot = head.indexOf('.');
    if (dot <= 0) return null;
    head = head.substring(0, dot);
    try {
      String id = new String(Base64.getUrlDecoder().decode(padded(head)), StandardCharsets.UTF_8);
      return id.matches("[0-9]{5,25}") ? id : null;
    } catch (IllegalArgumentException notBase64) {
      return null;
    }
  }

  private static String padded(String base64) {
    return base64 + "=".repeat((4 - base64.length() % 4) % 4);
  }
}
