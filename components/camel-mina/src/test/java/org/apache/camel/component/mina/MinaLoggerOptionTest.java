package org.apache.camel.component.mina;

import java.lang.reflect.Field;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.mina.common.IoSession;

/**
 * For unit testing the <tt>logger</tt> option.
 */
public class MinaLoggerOptionTest extends ContextTestSupport {

    public void testLoggerOptionTrue() throws Exception {
        final String uri = "mina:tcp://localhost:6321?textline=true&minaLogger=true";
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from(uri).to("mock:result");
            }
        });

        MockEndpoint mock = this.getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        Endpoint endpoint = context.getEndpoint(uri);
        Exchange exchange = endpoint.createExchange();
        Producer producer = endpoint.createProducer();
        producer.start();

        // set input and execute it
        exchange.getIn().setBody("Hello World");
        producer.process(exchange);

        Field field = producer.getClass().getDeclaredField("session");
        field.setAccessible(true);
        IoSession session = (IoSession) field.get(producer);
        assertTrue("There should be a logger filter", session.getFilterChain().contains("logger"));

        producer.stop();

        assertMockEndpointsSatisifed();
    }

    public void testLoggerOptionFalse() throws Exception {
        final String uri = "mina:tcp://localhost:6321?textline=true&minaLogger=false";
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from(uri).to("mock:result");
            }
        });

        MockEndpoint mock = this.getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        Endpoint endpoint = context.getEndpoint(uri);
        Exchange exchange = endpoint.createExchange();
        Producer producer = endpoint.createProducer();
        producer.start();

        // set input and execute it
        exchange.getIn().setBody("Hello World");
        producer.process(exchange);

        Field field = producer.getClass().getDeclaredField("session");
        field.setAccessible(true);
        IoSession session = (IoSession) field.get(producer);
        assertFalse("There should NOT be a logger filter", session.getFilterChain().contains("logger"));

        producer.stop();

        assertMockEndpointsSatisifed();
    }

    public void testNoLoggerOption() throws Exception {
        final String uri = "mina:tcp://localhost:6321?textline=true";
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from(uri).to("mock:result");
            }
        });

        MockEndpoint mock = this.getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        Endpoint endpoint = context.getEndpoint(uri);
        Exchange exchange = endpoint.createExchange();
        Producer producer = endpoint.createProducer();
        producer.start();

        // set input and execute it
        exchange.getIn().setBody("Hello World");
        producer.process(exchange);

        Field field = producer.getClass().getDeclaredField("session");
        field.setAccessible(true);
        IoSession session = (IoSession) field.get(producer);
        assertFalse("There should NOT default be a logger filter", session.getFilterChain().contains("logger"));

        producer.stop();

        assertMockEndpointsSatisifed();
    }


}
