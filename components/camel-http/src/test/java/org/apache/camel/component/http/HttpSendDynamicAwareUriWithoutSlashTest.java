package org.apache.camel.component.http;

import java.util.Map;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpSendDynamicAwareUriWithoutSlashTest extends BaseHttpTest {

    private HttpServer localServer;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
            .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
            .setExpectationVerifier(getHttpExpectationVerifier()).setSslContext(getSSLContext())
            .registerHandler("/users/*", new BasicValidationHandler("GET", null, null, "a user")).create();
        localServer.start();

        super.setUp();
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        if (localServer != null) {
            localServer.stop();
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:usersDrink")
                    .toD("http://localhost:" + localServer.getLocalPort()
                        + "/users/${exchangeProperty.user}");

                from("direct:usersDrinkWithoutSlash")
                    .toD("http:localhost:" + localServer.getLocalPort()
                        + "/users/${exchangeProperty.user}");
            }
        };
    }

    @Test
    public void testDynamicAware() throws Exception {
        Exchange out = fluentTemplate.to("direct:usersDrink").withExchange(ExchangeBuilder.anExchange(context).withProperty("user", "joes").build()).send();
        assertEquals("a user", out.getMessage().getBody(String.class));

        out = fluentTemplate.to("direct:usersDrink").withExchange(ExchangeBuilder.anExchange(context).withProperty("user", "moes").build()).send();
        assertEquals("a user", out.getMessage().getBody(String.class));

        // and there should only be one http endpoint as they are both on same host
        Map<String, Endpoint> endpointMap = context.getEndpointMap();
        assertTrue(endpointMap.containsKey("http://localhost:" + localServer.getLocalPort()), "Should find static uri");
        assertTrue(endpointMap.containsKey("direct://usersDrink"), "Should find direct");
        assertTrue(endpointMap.containsKey("direct://usersDrinkWithoutSlash"), "Should find direct");
        assertEquals(3, endpointMap.size());
    }
    
    @Test
    public void testDynamicAwareWithoutSlash() throws Exception {
        Exchange out = fluentTemplate.to("direct:usersDrinkWithoutSlash").withExchange(ExchangeBuilder.anExchange(context).withProperty("user", "joes").build()).send();
        assertEquals("a user", out.getMessage().getBody(String.class));

        out = fluentTemplate.to("direct:usersDrinkWithoutSlash").withExchange(ExchangeBuilder.anExchange(context).withProperty("user", "moes").build()).send();
        assertEquals("a user", out.getMessage().getBody(String.class));

        /*
            Using http:hostname[:port][/resourceUri][?options] as documented https://camel.apache.org/components/3.18.x/http-component.html stops the optimization
            
            org.apache.camel.http.base.HttpSendDynamicAware Line 158 breaks the logic
                
                URI parse = new URI(u);   
         */
        Map<String, Endpoint> endpointMap = context.getEndpointMap();
        assertTrue(endpointMap.containsKey("http://localhost:" + localServer.getLocalPort() + "/users/joes"), "Not optimized");
        assertTrue(endpointMap.containsKey("http://localhost:" + localServer.getLocalPort() + "/users/moes"), "Not optimized");
        assertTrue(endpointMap.containsKey("direct://usersDrink"), "Should find direct");
        assertTrue(endpointMap.containsKey("direct://usersDrinkWithoutSlash"), "Should find direct");
        assertEquals(4, endpointMap.size());
    }
}
