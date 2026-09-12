package dev.lovelace.lovecore.api.discord;

import java.time.Instant;
import java.util.List;

public record DiscordEmbed(
        String title,
        String description,
        Integer color,
        List<Field> fields,
        String footer,
        Instant timestamp
) {
    public record Field(String name, String value, boolean inline) {}

    public static DiscordEmbed simple(String title, String description, int color) {
        return new DiscordEmbed(title, description, color, List.of(), null, Instant.now());
    }
}
