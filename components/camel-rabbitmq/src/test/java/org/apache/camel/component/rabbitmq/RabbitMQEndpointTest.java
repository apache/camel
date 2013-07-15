package org.apache.camel.component.rabbitmq;

import com.rabbitmq.client.Envelope;
import org.apache.camel.Exchange;
import org.junit.Test;
import org.mockito.Mockito;

import java.net.URISyntaxException;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Stephen Samuel
 */
public class RabbitMQEndpointTest {

    private Envelope envelope = Mockito.mock(Envelope.class);

    @Test
    public void testCreatingRabbitExchangeSetsHeaders() throws URISyntaxException {
        RabbitMQEndpoint endpoint =
                new RabbitMQEndpoint("rabbitmq:localhost/exchange", "localhost/exchange", new RabbitMQComponent());

        String routingKey = UUID.randomUUID().toString();
        String exchangeName = UUID.randomUUID().toString();
        long tag = UUID.randomUUID().toString().hashCode();

        Mockito.when(envelope.getRoutingKey()).thenReturn(routingKey);
        Mockito.when(envelope.getExchange()).thenReturn(exchangeName);
        Mockito.when(envelope.getDeliveryTag()).thenReturn(tag);

        Exchange exchange = endpoint.createRabbitExchange(envelope);
        assertEquals(exchangeName, exchange.getIn().getHeader(RabbitMQConstants.EXCHANGE_NAME));
        assertEquals(routingKey, exchange.getIn().getHeader(RabbitMQConstants.ROUTING_KEY));
        assertEquals(tag, exchange.getIn().getHeader(RabbitMQConstants.DELIVERY_TAG));
    }

    @Test
    public void creatingExecutorUsesThreadPoolSettings() throws Exception {

        RabbitMQEndpoint endpoint =
                new RabbitMQEndpoint("rabbitmq:localhost/exchange", "localhost/exchange", new RabbitMQComponent());
        endpoint.setThreadPoolSize(400);
        ThreadPoolExecutor executor = endpoint.createExecutor();

        assertEquals(400, executor.getCorePoolSize());
    }

    @Test
    public void assertSingleton() throws URISyntaxException {
        RabbitMQEndpoint endpoint =
                new RabbitMQEndpoint("rabbitmq:localhost/exchange", "localhost/exchange", new RabbitMQComponent());
        assertTrue(endpoint.isSingleton());
    }
}
