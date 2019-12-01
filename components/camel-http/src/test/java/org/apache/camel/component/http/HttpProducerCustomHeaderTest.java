package org.apache.camel.component.http;

import org.apache.camel.Exchange;
import org.apache.camel.component.http.handler.HeaderValidationHandler;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.apache.http.HttpHeaders.HOST;

public class HttpProducerCustomHeaderTest extends BaseHttpTest {

    private static final String CUSTOM_HOST = "test";

    private HttpServer localServer;

    @Before
    @Override
    public void setUp() throws Exception {
        Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put(HOST,
                            CUSTOM_HOST);

        localServer = ServerBootstrap.bootstrap().
                setHttpProcessor(getBasicHttpProcessor()).
                setConnectionReuseStrategy(getConnectionReuseStrategy()).
                setResponseFactory(getHttpResponseFactory()).
                setExpectationVerifier(getHttpExpectationVerifier()).
                setSslContext(getSSLContext()).
                registerHandler("*",
                                new HeaderValidationHandler("GET",
                                                            "customHostHeader=test",
                                                            null,
                                                            getExpectedContent(),
                                                            expectedHeaders))
                .registerHandler("*",
                                 new HeaderValidationHandler("GET",
                                                             null,
                                                             null,
                                                             getExpectedContent(),
                                                             null))
                .create();

        localServer.start();

        super.setUp();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    public void testHttpProducer_GivenCustomHostHeaderQuery_SetCustomHost() throws Exception {

        HttpComponent component = context.getComponent("http",
                                                       HttpComponent.class);
        component.setConnectionTimeToLive(1000L);

        HttpEndpoint endpoint = (HttpEndpoint) component.createEndpoint("http://" + localServer.getInetAddress().getHostName() + ":"
                                                                                + localServer.getLocalPort() + "/myget?customHostHeader=test");
        HttpProducer producer = new HttpProducer(endpoint);

        Exchange exchange = producer.createExchange();
        exchange.getIn().setBody(null);

        producer.start();
        producer.process(exchange);
        producer.stop();

        assertExchange(exchange);
    }

    @Test
    public void testHttpProducer_GivenEmptyQuery_ShouldNotSetCustomHost() throws Exception {

        HttpComponent component = context.getComponent("http",
                                                       HttpComponent.class);
        component.setConnectionTimeToLive(1000L);

        HttpEndpoint endpoint = (HttpEndpoint) component.createEndpoint("http://" + localServer.getInetAddress().getHostName() + ":"
                                                                                + localServer.getLocalPort() + "/myget");
        HttpProducer producer = new HttpProducer(endpoint);

        Exchange exchange = producer.createExchange();
        exchange.getIn().setBody(null);

        producer.start();
        producer.process(exchange);
        producer.stop();

        assertExchange(exchange);
    }
}