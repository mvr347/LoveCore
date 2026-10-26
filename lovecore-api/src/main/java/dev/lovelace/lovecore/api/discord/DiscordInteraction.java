package dev.lovelace.lovecore.api.discord;
import java.util.Map;
public record DiscordInteraction(String id,String applicationId,String token,String userId,String guildId,
        String name,int type,Map<String,String> values) {
    public DiscordInteraction { values=Map.copyOf(values); }
    @Override public String toString(){return "DiscordInteraction["+id+", "+name+"]";}
}
