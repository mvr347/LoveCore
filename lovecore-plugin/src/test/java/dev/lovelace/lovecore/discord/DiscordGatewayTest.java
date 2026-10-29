package dev.lovelace.lovecore.discord;
import com.google.gson.*;
import dev.lovelace.lovecore.api.discord.*;
import org.junit.jupiter.api.Test;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
class DiscordGatewayTest {
    @Test void authenticatedMemberAndFormValuesAreExtracted() {
        AtomicReference<DiscordInteraction> found=new AtomicReference<>();
        try(var client=HttpClient.newHttpClient();var gateway=new DiscordGateway(client,"unused",found::set)) {
            String payload="""
                {"op":0,"t":"INTERACTION_CREATE","s":1,"d":{"id":"1","application_id":"2","token":"secret-value","type":5,"guild_id":"3","member":{"user":{"id":"4"}},"data":{"custom_id":"lwa:appeal:submit:12","components":[{"components":[{"custom_id":"reason","value":"Проверьте блокировку"}]}]}}}
                """;
            gateway.onText(new Socket(),payload,true);
            assertEquals("4",found.get().userId());assertEquals("Проверьте блокировку",found.get().values().get("reason"));
            assertFalse(found.get().toString().contains("secret-value"));
            assertThrows(UnsupportedOperationException.class,()->found.get().values().put("x","y"));
        }
    }
    @Test void dmInteractionUsesUserAndFragmentedFrames() {
        AtomicReference<DiscordInteraction> found=new AtomicReference<>();
        try(var client=HttpClient.newHttpClient();var gateway=new DiscordGateway(client,"unused",found::set)) {
            String p="{\"op\":0,\"t\":\"INTERACTION_CREATE\",\"d\":{\"id\":\"1\",\"application_id\":\"2\",\"token\":\"secret\",\"type\":3,\"user\":{\"id\":\"55\"},\"data\":{\"custom_id\":\"lwa:appeal:new:1\"}}}";
            gateway.onText(new Socket(),p.substring(0,30),false);assertNull(found.get());
            gateway.onText(new Socket(),p.substring(30),true);assertEquals("55",found.get().userId());assertEquals("",found.get().guildId());
        }
    }

  @Test
  void channelContextIsTakenFromDiscordNotSubmittedFields() {
    AtomicReference<DiscordInteraction> found = new AtomicReference<>();
    try (var client = HttpClient.newHttpClient();
        var gateway = new DiscordGateway(client, "unused", found::set)) {
      String p =
          """
                {"op":0,"t":"INTERACTION_CREATE","d":{"id":"1","application_id":"2","token":"secret","type":5,"channel_id":"12345","guild_id":"33333","member":{"user":{"id":"44444"}},"data":{"custom_id":"lwa:desk:submit:close:report:1","components":[{"components":[{"custom_id":"_channelId","value":"99999"}]}]}}}
                """;
      gateway.onText(new Socket(), p, true);
      assertEquals("12345", found.get().values().get("_channelId"));
    }
  }

    static class Socket implements WebSocket {
        public CompletableFuture<WebSocket> sendText(CharSequence x,boolean last){return CompletableFuture.completedFuture(this);}
        public CompletableFuture<WebSocket> sendBinary(ByteBuffer x,boolean last){return CompletableFuture.completedFuture(this);}
        public CompletableFuture<WebSocket> sendPing(ByteBuffer x){return CompletableFuture.completedFuture(this);}
        public CompletableFuture<WebSocket> sendPong(ByteBuffer x){return CompletableFuture.completedFuture(this);}
        public CompletableFuture<WebSocket> sendClose(int x,String reason){return CompletableFuture.completedFuture(this);}
        public void request(long n){}public String getSubprotocol(){return "";}public boolean isOutputClosed(){return false;}public boolean isInputClosed(){return false;}public void abort(){}
    }
}
