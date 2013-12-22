package org.apache.camel.component.kafka;

import java.net.URISyntaxException;
import java.util.Properties;

import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultMessage;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.Mockito;

import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;

import static org.junit.Assert.assertEquals;

/**
 * @author Stephen Samuel
 */
public class KafkaProducerTest {

    private KafkaProducer producer;
    private KafkaEndpoint endpoint;

    private Exchange exchange = Mockito.mock(Exchange.class);
    private Message in = new DefaultMessage();

    public KafkaProducerTest() throws IllegalAccessException, InstantiationException, ClassNotFoundException,
            URISyntaxException {
        endpoint = new KafkaEndpoint("kafka:broker1:1234,broker2:4567?topic=sometopic",
                "broker1:1234," + "broker2:4567?topic=sometopic", null);
        producer = new KafkaProducer(endpoint);
        producer.producer = Mockito.mock(Producer.class);
    }

    @Test
    public void testPropertyBuilder() throws Exception {
        endpoint.setPartitioner("com.sksamuel.someclass");
        Properties props = producer.getProps();
        assertEquals("com.sksamuel.someclass", props.getProperty("partitioner.class"));
        assertEquals("broker1:1234,broker2:4567", props.getProperty("metadata.broker.list"));
    }

    @Test
    public void processSendsMesssage() throws Exception {

        endpoint.setTopic("sometopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        in.setHeader(KafkaConstants.PARTITION_KEY, "4");

        producer.process(exchange);

        Mockito.verify(producer.producer).send(Matchers.any(KeyedMessage.class));
    }

    @Test(expected = CamelException.class)
    public void processRequiresPartitionHeader() throws Exception {
        endpoint.setTopic("sometopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        producer.process(exchange);
    }

    @Test
    public void processSendsMesssageWithPartitionKeyHeader() throws Exception {

        endpoint.setTopic("sometopic");
        Mockito.when(exchange.getIn()).thenReturn(in);
        in.setHeader(KafkaConstants.PARTITION_KEY, "4");

        producer.process(exchange);

        ArgumentCaptor<KeyedMessage> captor = ArgumentCaptor.forClass(KeyedMessage.class);
        Mockito.verify(producer.producer).send(captor.capture());
        assertEquals("4", captor.getValue().key());
        assertEquals("sometopic", captor.getValue().topic());
    }
}
