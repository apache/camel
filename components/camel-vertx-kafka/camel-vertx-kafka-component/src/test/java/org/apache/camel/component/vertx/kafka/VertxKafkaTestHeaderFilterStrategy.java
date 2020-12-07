package org.apache.camel.component.vertx.kafka;

import java.util.regex.Pattern;

import org.apache.camel.support.DefaultHeaderFilterStrategy;

public class VertxKafkaTestHeaderFilterStrategy extends DefaultHeaderFilterStrategy {

    public static final Pattern CAMEL_KAFKA_FILTER_PATTERN
            = Pattern.compile("(?i)(TEST|test|Test\\.)[\\.|a-z|A-z|0-9]*");

    public VertxKafkaTestHeaderFilterStrategy() {
        initialize();
    }

    protected void initialize() {
        // filter out kafka record metadata
        getInFilter().add("org.apache.kafka.clients.producer.RecordMetadata");

        setOutFilterPattern(CAMEL_KAFKA_FILTER_PATTERN);
        setInFilterPattern(CAMEL_KAFKA_FILTER_PATTERN);
    }
}
