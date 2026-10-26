package dev.lovelace.lovecore.discord;
import org.junit.jupiter.api.Test;
import dev.lovelace.lovecore.api.discord.DiscordEmbed;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DiscordPayloadTest {
    @Test void textLimitsDoNotSplitSurrogatePairs(){assertEquals("a",DiscordServiceImpl.cap("a🏰b",2));assertEquals("a🏰",DiscordServiceImpl.cap("a🏰b",3));}
    @Test void embedRespectsPerFieldAndCombinedLimits(){
        try(var service=new DiscordServiceImpl(null)){
            var fields=new ArrayList<DiscordEmbed.Field>();for(int i=0;i<40;i++)fields.add(new DiscordEmbed.Field("n".repeat(300),"v".repeat(2000),false));
            var json=service.serializeEmbed(new DiscordEmbed("t".repeat(400),"d".repeat(5000),123,fields,"f".repeat(3000),null));
            int length=json.get("title").getAsString().length()+json.get("description").getAsString().length();
            assertTrue(json.get("title").getAsString().length()<=256);assertTrue(json.get("description").getAsString().length()<=4096);
            for(var field:json.getAsJsonArray("fields")){var f=field.getAsJsonObject();assertTrue(f.get("name").getAsString().length()<=256);assertTrue(f.get("value").getAsString().length()<=1024);length+=f.get("name").getAsString().length()+f.get("value").getAsString().length();}
            if(json.has("footer"))length+=json.getAsJsonObject("footer").get("text").getAsString().length();assertTrue(length<=6000);
        }
    }
}
