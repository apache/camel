package org.apache.camel.component.vertx.kafka.operations;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import io.vertx.kafka.client.producer.KafkaProducer;
import org.apache.camel.Exchange;
import org.apache.camel.component.vertx.kafka.VertxKafkaConstants;
import org.apache.camel.component.vertx.kafka.configuration.VertxKafkaConfiguration;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VertxKafkaProducerOperationsTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(VertxKafkaProducerOperationsTest.class);

    private final VertxKafkaConfiguration configuration = new VertxKafkaConfiguration();
    private final MockProducer<Object, Object> mockProducer = new MockProducer<>();
    private final KafkaProducer<Object, Object> producer = KafkaProducer.create(Vertx.vertx(), mockProducer);

    @BeforeAll
    void prepare() {
        configuration.setKeySerializer(StringSerializer.class.getName());
        configuration.setValueSerializer(StringSerializer.class.getName());
        configuration.setKeyDeserializer(StringDeserializer.class.getName());
        configuration.setValueDeserializer(StringDeserializer.class.getName());
    }

    @Test
    void testSendSimpleEvents() {
        configuration.setTopic("testSimpleEventsTopic");
        final VertxKafkaProducerOperations operations = new VertxKafkaProducerOperations(producer, configuration);

        final Exchange exchange = new DefaultExchange(context);
        final List<String> messages = new LinkedList<>();
        messages.add("test message 1");
        messages.add("test message 2");
        messages.add("test message 3");
        messages.add("test message 4");
        messages.add("test message 5");

        exchange.getIn().setBody(messages);

        operations.sentEvents(exchange, doneSync -> {});

        final Exchange exchange2 = new DefaultExchange(context);

        exchange2.getIn().setHeader(VertxKafkaConstants.MESSAGE_KEY, "6");
        exchange2.getIn().setBody("test message 6");

        operations.sentEvents(exchange2, doneSync -> {});

        Awaitility
                .await()
                .atMost(2, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> {
                    if (!mockProducer.history().isEmpty()) {
                        final List<ProducerRecord<Object, Object>> records = mockProducer.history();
                        // assert the size
                        assertEquals(records.size(), 6);

                        // assert the content
                        final ProducerRecord<Object, Object> record1 = records.get(0);
                        final ProducerRecord<Object, Object> record2 = records.get(1);
                        final ProducerRecord<Object, Object> record3 = records.get(2);
                        final ProducerRecord<Object, Object> record4 = records.get(3);
                        final ProducerRecord<Object, Object> record5 = records.get(4);
                        final ProducerRecord<Object, Object> record6 = records.get(5);

                        assertEquals("test message 1", record1.value().toString());
                        assertEquals("test message 2", record2.value().toString());
                        assertEquals("test message 3", record3.value().toString());
                        assertEquals("test message 4", record4.value().toString());
                        assertEquals("test message 5", record5.value().toString());
                        assertEquals("test message 6", record6.value().toString());

                        assertNull(record1.key());
                        assertNull(record2.key());
                        assertNull(record3.key());
                        assertNull(record4.key());
                        assertNull(record5.key());
                        assertEquals("6", record6.key().toString());

                        records.forEach(record -> {
                            // assert is the correct topic
                            assertEquals("testSimpleEventsTopic", record.topic());
                            assertNull(record.partition());
                        });

                        return true;
                    }
                    return false;
                });
    }

    @Test
    void testEventsWithKeyInfo() {

    }

}
