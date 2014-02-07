package org.apache.camel.component.kafka;

import java.net.URISyntaxException;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.camel.Exchange;
import org.junit.Test;

import kafka.message.MessageAndMetadata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Stephen Samuel
 */
public class KafkaEndpointTest {

    @Test
    public void testCreatingKafkaExchangeSetsHeaders() throws URISyntaxException {
        KafkaEndpoint endpoint = new KafkaEndpoint("kafka:localhost", "localhost", new KafkaComponent());

        MessageAndMetadata<byte[], byte[]> mm =
                new MessageAndMetadata<byte[], byte[]>("somekey".getBytes(), "mymessage".getBytes(), "topic", 4, 56);

        Exchange exchange = endpoint.createKafkaExchange(mm);
        assertEquals("somekey", exchange.getIn().getHeader(KafkaConstants.KEY));
        assertEquals("topic", exchange.getIn().getHeader(KafkaConstants.TOPIC));
        assertEquals(4, exchange.getIn().getHeader(KafkaConstants.PARTITION));
    }

    @Test
    public void creatingExecutorUsesThreadPoolSettings() throws Exception {
        KafkaEndpoint endpoint = new KafkaEndpoint("kafka:localhost", "kafka:localhost", new KafkaComponent());
        endpoint.setConsumerStreams(44);
        ThreadPoolExecutor executor = endpoint.createExecutor();
        assertEquals(44, executor.getCorePoolSize());
    }

    @Test
    public void assertSingleton() throws URISyntaxException {
        KafkaEndpoint endpoint = new KafkaEndpoint("kafka:localhost", "localhost", new KafkaComponent());
        assertTrue(endpoint.isSingleton());
    }
}
