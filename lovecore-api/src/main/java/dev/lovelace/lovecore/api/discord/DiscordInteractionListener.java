package dev.lovelace.lovecore.api.discord;
@FunctionalInterface public interface DiscordInteractionListener {
    /** Return false when the interaction belongs to another plugin. Respond within 3 seconds. */
    boolean onInteraction(DiscordInteraction interaction);
}
