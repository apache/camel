package org.apache.camel.maven.component.vertx.kafka.config;

import java.util.Set;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigUtilsTest {

    @Test
    void testExtractOnlyConsumerFields() {
        final Set<String> fields = ConfigUtils.extractProducerOnlyFields(ConsumerConfig.configNames(), ProducerConfig.configNames());
    }
}