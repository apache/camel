package org.apache.camel.component.vertx.kafka.operations;

import java.util.LinkedList;
import java.util.List;

import io.vertx.core.Vertx;
import io.vertx.kafka.client.producer.KafkaProducer;
import org.apache.camel.Exchange;
import org.apache.camel.component.vertx.kafka.configuration.VertxKafkaConfiguration;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VertxKafkaProducerOperationsTest extends CamelTestSupport {

    private VertxKafkaConfiguration configuration;
    private KafkaProducer<Object, Object> kafkaProducer;

    @BeforeAll
    void prepare() {
        configuration = new VertxKafkaConfiguration();
        configuration.setTopic("test-2");
        configuration.setBootstrapServers("localhost:9092");
        configuration.setKeySerializer(StringSerializer.class.getName());
        configuration.setValueSerializer(StringSerializer.class.getName());
        configuration.setKeyDeserializer(StringDeserializer.class.getName());
        configuration.setValueDeserializer(StringDeserializer.class.getName());

        kafkaProducer = KafkaProducer.create(Vertx.vertx(), configuration.createProducerConfiguration());
    }

    @Test
    void testSendEvents() throws InterruptedException {
        final VertxKafkaProducerOperations operations = new VertxKafkaProducerOperations(kafkaProducer, configuration);

        final Exchange exchange = new DefaultExchange(context);
        final List<String> messages = new LinkedList<>();
        messages.add("test message 1");
        messages.add("test message 2");
        messages.add("test message 3");
        messages.add("test message 4");
        messages.add("test message 5");

        exchange.getIn().setBody(messages);

        operations.sentEvents(exchange, doneSync -> {
        });

        //Thread.sleep(5000);
    }
}
