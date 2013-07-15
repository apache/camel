package org.apache.camel.component.rabbitmq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author Stephen Samuel
 */
public class RabbitMQConsumerIntTest extends CamelTestSupport {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQConsumerIntTest.class);
    private static final String EXCHANGE = "ex1";

    @EndpointInject(uri = "rabbitmq:localhost:5672/" + EXCHANGE + "?username=cameltest&password=cameltest")
    private Endpoint from;

    @EndpointInject(uri = "mock:result")
    private MockEndpoint to;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from(from).to(to);
            }
        };
    }

    @Test
    public void sentMessageIsReceived() throws InterruptedException, IOException {

        to.expectedMessageCount(1);

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5672);
        factory.setUsername("cameltest");
        factory.setPassword("cameltest");
        factory.setVirtualHost("/");
        Connection conn = factory.newConnection();

        AMQP.BasicProperties.Builder properties = new AMQP.BasicProperties.Builder();

        Channel channel = conn.createChannel();
        channel.basicPublish(EXCHANGE, "", properties.build(), "hello world".getBytes());

        to.assertIsSatisfied();
    }
}

