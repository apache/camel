package org.apache.camel.component.rabbitmq;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Integration test to confirm REQUEUE header causes message to be re-queued instead of sent to DLQ.
 *
 * Created by Andrew Austin on 2/21/15.
 */
public class RabbitMQRequeueIntTest extends CamelTestSupport {
    public static final String ROUTING_KEY = "rk4";

    @Produce(uri = "direct:rabbitMQ")
    protected ProducerTemplate directProducer;

    @EndpointInject(uri = "rabbitmq:localhost:5672/ex4?username=cameltest&password=cameltest"
            + "&autoAck=false&queue=q4&routingKey=" + ROUTING_KEY)
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
                        .log("Sending message")
                        .inOnly(rabbitMQEndpoint)
                        .to(producingMockEndpoint);
                from(rabbitMQEndpoint)
                        .id("consumingRoute")
                        .log("Receiving message")
                        .inOnly(consumingMockEndpoint)
                        .choice()
                        .when(body().isEqualTo("requeue header false"))
                            .log("Setting REQUEUE flag to false")
                            .setHeader(RabbitMQConstants.REQUEUE, constant(false))
                        .when(body().isEqualTo("requeue header true"))
                            .log("Setting REQUEUE flag to true")
                            .setHeader(RabbitMQConstants.REQUEUE, constant(true))
                        .when(body().isEqualTo("non-boolean header"))
                                .log("Setting REQUEUE flag to non-boolean")
                                .setHeader(RabbitMQConstants.REQUEUE, constant(4l))
                        .end()
                        .throwException(new Exception("Simulated exception"));
            }
        };
    }

    @Test
    public void testNoRequeueHeaderCausesReject() throws Exception {
        producingMockEndpoint.expectedMessageCount(1);
        consumingMockEndpoint.expectedMessageCount(1);

        directProducer.sendBody("no requeue header");

        Thread.sleep(100);
        producingMockEndpoint.assertIsSatisfied();
        consumingMockEndpoint.assertIsSatisfied();
    }

    @Test
    public void testNonBooleanRequeueHeaderCausesReject() throws Exception {
        producingMockEndpoint.expectedMessageCount(1);
        consumingMockEndpoint.expectedMessageCount(1);

        directProducer.sendBody("non-boolean header");

        Thread.sleep(100);
        producingMockEndpoint.assertIsSatisfied();
        consumingMockEndpoint.assertIsSatisfied();
    }

    @Test
    public void testFalseRequeueHeaderCausesReject() throws Exception {
        producingMockEndpoint.expectedMessageCount(1);
        consumingMockEndpoint.expectedMessageCount(1);

        directProducer.sendBody("non-boolean header");

        Thread.sleep(100);
        producingMockEndpoint.assertIsSatisfied();
        consumingMockEndpoint.assertIsSatisfied();
    }

    @Test
    public void testTrueRequeueHeaderCausesRequeue() throws Exception {
        producingMockEndpoint.expectedMessageCount(1);
        consumingMockEndpoint.setMinimumExpectedMessageCount(2);

        directProducer.sendBody("requeue header true");

        Thread.sleep(100);
        producingMockEndpoint.assertIsSatisfied();
        consumingMockEndpoint.assertIsSatisfied();
    }
}
