package org.apache.camel.component.nsq;

import com.github.brainlag.nsq.NSQProducer;
import com.github.brainlag.nsq.exceptions.NSQException;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

import java.util.concurrent.TimeoutException;

public class NsqConsumerTest extends NsqTestSupport {

    private static final int NUMBER_OF_MESSAGES = 10000;

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint mockResultEndpoint;

    @Test
    public void testConsumer() throws NSQException, TimeoutException, InterruptedException {
        mockResultEndpoint.expectedMessageCount(1);
        mockResultEndpoint.setAssertPeriod(5000);

        NSQProducer producer = new NSQProducer();
        producer.addAddress("localhost", 4150);
        producer.start();

        producer.produce("test", ("Hello NSQ!").getBytes());

        mockResultEndpoint.assertIsSatisfied();

        assertEquals("Hello NSQ!", mockResultEndpoint.getReceivedExchanges().get(0).getIn().getBody(String.class));
    }

    @Test
    public void testLoadConsumer() throws NSQException, TimeoutException, InterruptedException {
        mockResultEndpoint.setExpectedMessageCount(NUMBER_OF_MESSAGES);
        mockResultEndpoint.setAssertPeriod(5000);

        NSQProducer producer = new NSQProducer();
        producer.addAddress("localhost", 4150);
        producer.start();

        for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
            producer.produce("test", ("test" + i).getBytes());
        }

        mockResultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                fromF("nsq://%s?topic=%s&lookupInterval=5s", getNsqConsumerUrl(), "test").to(mockResultEndpoint);
            }
        };
    }
}
