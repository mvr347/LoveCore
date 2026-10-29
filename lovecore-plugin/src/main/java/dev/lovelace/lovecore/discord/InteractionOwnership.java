package dev.lovelace.lovecore.discord;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides which Discord interactions LoveCore may answer with its own "unavailable" reply.
 * <p>
 * The same bot token is shared with other gateways (LoveAuth runs its own JDA), and Discord delivers
 * every interaction to all of them. Answering one that belongs to someone else acknowledges it first
 * and makes the real owner fail with error 40060, so only slash commands registered through LoveCore
 * and component/modal ids under a claimed prefix count as ours.
 */
final class InteractionOwnership {

    /** Discord interaction type of an application (slash) command. */
    static final int APPLICATION_COMMAND = 2;

    private final Set<String> commands = ConcurrentHashMap.newKeySet();
    private final Set<String> prefixes = ConcurrentHashMap.newKeySet();

    void addCommand(String name) {
        if (name != null && !name.isBlank()) commands.add(name);
    }

    void removeCommand(String name) {
        if (name != null) commands.remove(name);
    }

    void claimPrefix(String prefix) {
        if (prefix != null && !prefix.isBlank()) prefixes.add(prefix);
    }

    boolean owns(int type, String name) {
        if (name == null) return false;
        if (type == APPLICATION_COMMAND) return commands.contains(name);
        for (String prefix : prefixes) if (name.startsWith(prefix)) return true;
        return false;
    }
}
