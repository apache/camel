package org.apache.camel.component.kafka;

import java.util.concurrent.ThreadPoolExecutor;

import org.apache.camel.Processor;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.*;

/**
 * @author Stephen Samuel
 */
public class KafkaConsumerTest {

    private KafkaEndpoint endpoint = mock(KafkaEndpoint.class);
    private Processor processor = mock(Processor.class);

    @Test(expected = IllegalArgumentException.class)
    public void consumerRequiresZookeeperHost() throws Exception {
        Mockito.when(endpoint.getZookeeperPort()).thenReturn(2181);
        new KafkaConsumer(endpoint, processor);
    }

    @Test(expected = IllegalArgumentException.class)
    public void consumerRequiresZookeeperPort() throws Exception {
        Mockito.when(endpoint.getZookeeperHost()).thenReturn("localhost");
        new KafkaConsumer(endpoint, processor);
    }

    @Test
    public void testStoppingConsumerShutsdownExecutor() throws Exception {

        when(endpoint.getZookeeperHost()).thenReturn("localhost");
        when(endpoint.getZookeeperPort()).thenReturn(2181);
        when(endpoint.getGroupId()).thenReturn("12345");

        KafkaConsumer consumer = new KafkaConsumer(endpoint, processor);

        ThreadPoolExecutor e = mock(ThreadPoolExecutor.class);
        consumer.executor = e;
        consumer.doStop();
        verify(e).shutdown();
    }
}
