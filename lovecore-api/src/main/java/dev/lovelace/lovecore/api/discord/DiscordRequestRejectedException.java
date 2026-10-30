package dev.lovelace.lovecore.api.discord;

/**
 * A definitive rejection: Discord did not create a message. Never contains tokens or response
 * bodies.
 */
public final class DiscordRequestRejectedException extends IllegalStateException {
  private final int statusCode;

  public DiscordRequestRejectedException(int statusCode) {
    super(
        switch (statusCode) {
          case 400, 413, 422 -> "Discord отклонил формат сообщения (HTTP " + statusCode + ")";
          case 401 -> "Discord отклонил токен бота (HTTP 401)";
          case 403 -> "Боту недоступен канал или не хватает прав (HTTP 403)";
          case 404 -> "Канал Discord не найден (HTTP 404)";
          case 429 -> "Discord ограничил частоту запросов (HTTP 429)";
          default -> throw new IllegalArgumentException("Not a definitive Discord rejection");
        });
    this.statusCode = statusCode;
  }

  public int getStatusCode() {
    return statusCode;
  }
}
