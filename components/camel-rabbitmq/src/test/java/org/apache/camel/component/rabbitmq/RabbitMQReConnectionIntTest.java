package org.apache.camel.component.rabbitmq;

import com.rabbitmq.client.AlreadyClosedException;
import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import java.net.ConnectException;
import java.util.concurrent.TimeUnit;

/**
 * Integration test to check that RabbitMQ Endpoint is able to reconnect to broker when broker
 * is not avaibable.
 * <ul>
 * <li>Stop the broker</li>
 * <li>Run the test: the producer complains it can not send messages, the consumer is silent</li>
 * <li>Start the broker: the producer sends messages, and the consumer receives messages</li>
 * <li>Stop the broker: the producer complains it can not send messages, the consumer is silent</li>
 * <li>Start the broker: the producer sends messages, and the consumer receives messages</li>
 * </ul>
 */
public class RabbitMQReConnectionIntTest extends CamelTestSupport {
    private static final String EXCHANGE = "ex3";

    @Produce(uri = "direct:rabbitMQ")
    protected ProducerTemplate directProducer;

    @EndpointInject(uri = "rabbitmq:localhost:5672/" + EXCHANGE + "?username=cameltest&password=cameltest" +
            "&queue=q3&routingKey=rk3" +
            "&automaticRecoveryEnabled=true" +
            "&requestedHeartbeat=1000" +
            "&connectionTimeout=5000")
    private Endpoint rabbitMQEndpoint;

    @EndpointInject(uri = "mock:producing")
    private MockEndpoint producingMockEndpoint;

    @EndpointInject(uri = "mock:consuming")
    private MockEndpoint consumingMockEndpoint;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("direct:rabbitMQ")
                        .id("producingRoute")
                        .onException(AlreadyClosedException.class, ConnectException.class)
                        .maximumRedeliveries(10)
                        .redeliveryDelay(500L)
                        .end()
                        .log("Sending message")
                        .inOnly(rabbitMQEndpoint)
                        .to(producingMockEndpoint);
                from(rabbitMQEndpoint)
                        .id("consumingRoute")
                        .log("Receiving message")
                        .to(consumingMockEndpoint);
            }
        };
    }

    @Test
    public void testSendEndReceive() throws Exception {
        int nbMessages = 50;
        int failedMessages = 0;
        for (int i = 0; i < nbMessages; i++) {
            try {
                directProducer.sendBodyAndHeader("Message #" + i, RabbitMQConstants.ROUTING_KEY, "rk3");
            } catch (CamelExecutionException e) {
                log.debug("Can not send message", e);
                failedMessages++;
            }
            Thread.sleep(500L);
        }
        producingMockEndpoint.expectedMessageCount(nbMessages - failedMessages);
        consumingMockEndpoint.expectedMessageCount(nbMessages - failedMessages);
        assertMockEndpointsSatisfied(5, TimeUnit.SECONDS);
    }
}
