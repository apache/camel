package org.apache.camel.component.telegram;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.telegram.model.Chat;
import org.apache.camel.component.telegram.model.IncomingMessage;
import org.apache.camel.component.telegram.model.UpdateResult;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.junit.Before;
import org.junit.Test;

public class TelegramConsumerChannelPostTest extends TelegramTestSupport{

	@EndpointInject(uri = "mock:telegram")
    private MockEndpoint endpoint;

    @Before
    public void mockAPIs() {
        TelegramService api = mockTelegramService();

        UpdateResult res1 = getJSONResource("messages/updates-channelMessage.json", UpdateResult.class);

        UpdateResult defaultRes = getJSONResource("messages/updates-empty.json", UpdateResult.class);

        when(api.getUpdates(any(), any(), any(), any())).thenReturn(res1).thenAnswer((i) -> defaultRes);
    }
    
    @Test
    public void testReceptionOfMessageWithAMessage() throws Exception {
        endpoint.expectedMinimumMessageCount(1);
        endpoint.assertIsSatisfied();

        Exchange mediaExchange = endpoint.getExchanges().get(0);
        IncomingMessage msg = mediaExchange.getIn().getBody(IncomingMessage.class);
        
        assertEquals("-1001245756934", mediaExchange.getIn().getHeader(TelegramConstants.TELEGRAM_CHAT_ID));
        
        //checking body
        assertNotNull(msg);
        assertEquals("test", msg.getText());
        assertEquals(Long.valueOf(67L), msg.getMessageId());
        assertEquals(Instant.ofEpochSecond(1546505413L), msg.getDate());
        
        // checking chat
        Chat chat = msg.getChat();
        assertNotNull(chat);
        assertEquals("-1001245756934", chat.getId());
        assertEquals("cameltemp", chat.getTitle());
        assertEquals("channel", chat.getType());

    }
    
    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("telegram:bots/mock-token")
                        .to("mock:telegram");
            }
        };
    }
}
