package org.apache.camel.component.vertx.kafka;

import java.util.regex.Pattern;

import org.apache.camel.support.DefaultHeaderFilterStrategy;

public class VertxKafkaHeaderFilterStrategy extends DefaultHeaderFilterStrategy {

    /**
     * A filter pattern that only accepts keys starting with <tt>Camel</tt> or <tt>org.apache.camel.</tt>
     */
    public static final Pattern CAMEL_KAFKA_FILTER_PATTERN
            = Pattern.compile("(?i)(Camel|org\\.apache\\.camel|kafka\\.)[\\.|a-z|A-z|0-9]*");

    public VertxKafkaHeaderFilterStrategy() {
        initialize();
    }

    protected void initialize() {
        // filter out kafka record metadata
        getInFilter().add("org.apache.kafka.clients.producer.RecordMetadata");

        // filter headers beginning with "Camel" or "org.apache.camel" or "kafka."
        setOutFilterPattern(CAMEL_KAFKA_FILTER_PATTERN);
        setInFilterPattern(CAMEL_KAFKA_FILTER_PATTERN);
    }
}
