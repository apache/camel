package org.apache.camel.component.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.apache.camel.Processor;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Stephen Samuel
 */
public class RabbitMQConsumerTest {

    private RabbitMQEndpoint endpoint = Mockito.mock(RabbitMQEndpoint.class);
    private Connection conn = Mockito.mock(Connection.class);
    private Processor processor = Mockito.mock(Processor.class);
    private Channel channel = Mockito.mock(Channel.class);

    @Test
    public void testStoppingConsumerShutsdownExecutor() throws Exception {
        RabbitMQConsumer consumer = new RabbitMQConsumer(endpoint, processor);

        ThreadPoolExecutor e = (ThreadPoolExecutor) Executors.newFixedThreadPool(3);
        Mockito.when(endpoint.createExecutor()).thenReturn(e);
        Mockito.when(endpoint.connect(Matchers.any(ExecutorService.class))).thenReturn(conn);
        Mockito.when(conn.createChannel()).thenReturn(channel);

        consumer.doStart();
        assertFalse(e.isShutdown());

        consumer.doStop();
        assertTrue(e.isShutdown());
    }

    @Test
    public void testStoppingConsumerShutsdownConnection() throws Exception {
        RabbitMQConsumer consumer = new RabbitMQConsumer(endpoint, processor);

        Mockito.when(endpoint.createExecutor()).thenReturn((ThreadPoolExecutor) Executors.newFixedThreadPool(3));
        Mockito.when(endpoint.connect(Matchers.any(ExecutorService.class))).thenReturn(conn);
        Mockito.when(conn.createChannel()).thenReturn(channel);

        consumer.doStart();
        consumer.doStop();

        Mockito.verify(conn).close();
    }
}
