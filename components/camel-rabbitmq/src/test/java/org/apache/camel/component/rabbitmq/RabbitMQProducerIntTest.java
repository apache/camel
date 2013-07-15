package org.apache.camel.component.rabbitmq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Stephen Samuel
 */
public class RabbitMQProducerIntTest extends CamelTestSupport {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQProducerIntTest.class);
    private static final String EXCHANGE = "ex1";

    @EndpointInject(uri = "rabbitmq:localhost:5672/" + EXCHANGE + "?username=cameltest&password=cameltest")
    private Endpoint to;

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("direct:start").to(to);
            }
        };
    }

    @Test
    public void producedMessageIsReceived() throws InterruptedException, IOException {

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5672);
        factory.setUsername("cameltest");
        factory.setPassword("cameltest");
        factory.setVirtualHost("/");
        Connection conn = factory.newConnection();

        final List received = new ArrayList();

        Channel channel = conn.createChannel();
        channel.queueDeclare("sammyq", false, false, true, null);
        channel.queueBind("sammyq", EXCHANGE, "");
        channel.basicConsume("sammyq", true, new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag,
                                       Envelope envelope,
                                       AMQP.BasicProperties properties,
                                       byte[] body) throws IOException {
                received.add(envelope);
            }
        });

        template.sendBodyAndHeader("new message", RabbitMQConstants.EXCHANGE_NAME, "ex1");
        Thread.sleep(500);
        assertEquals(1, received.size());
    }
}

