package org.apache.camel.component.websocket;

import de.roderick.weberknecht.WebSocketConnection;
import de.roderick.weberknecht.WebSocketEventHandler;
import de.roderick.weberknecht.WebSocketException;
import de.roderick.weberknecht.WebSocketMessage;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.CamelTestSupport;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;

public class WebsocketClientCamelRouteTest extends CamelTestSupport {

    private static URI uriWS;
    private static WebSocketConnection webSocketConnection;

    @Test
    public void testWSHttpCall() throws WebSocketException {

        try {
            uriWS = new URI("ws://127.0.0.1:9292/test");
            WebSocketConnection webSocketConnection = new WebSocketConnection(uriWS);

            // Register Event Handlers
            webSocketConnection.setEventHandler(new WebSocketEventHandler() {
                public void onOpen() {
                    System.out.println("--open");
                }

                public void onMessage(WebSocketMessage message) {
                    System.out.println("--received message: " + message.getText());
                }

                public void onClose() {
                    System.out.println("--close");
                }
            });

            // Establish WebSocket Connection
            webSocketConnection.connect();
            System.out.println(">>> Connection established.");

                        // Send Data
            webSocketConnection.send("Hello from WS Client");


        } catch (WebSocketException ex) {
            ex.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }


    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("websocket://test")
                    .setExchangePattern(ExchangePattern.InOut)
                    .log(">>> Message received from WebSocket Client : ${body}")
/*                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                           String response = ">> welcome on board";
                            exchange.getOut().setBody(response);
                            exchange.getIn().setBody(response);
                        }
                    });*/
                    .loop(10)
                        .setBody().constant(">> Welcome on board !")
                        .to("websocket://test");

            }
        };
    }


}
