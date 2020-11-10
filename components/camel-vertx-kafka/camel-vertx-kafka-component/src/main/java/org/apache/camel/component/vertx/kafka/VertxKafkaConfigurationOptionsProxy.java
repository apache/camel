package org.apache.camel.component.vertx.kafka;

import java.util.function.Supplier;

import org.apache.camel.Exchange;
import org.apache.camel.component.vertx.kafka.configuration.VertxKafkaConfiguration;
import org.apache.camel.util.ObjectHelper;

public class VertxKafkaConfigurationOptionsProxy {

    private final VertxKafkaConfiguration configuration;

    public VertxKafkaConfigurationOptionsProxy(final VertxKafkaConfiguration configuration) {
        this.configuration = configuration;
    }

    public Integer getPartitionId(final Exchange exchange) {
        return getOption(exchange, VertxKafkaConstants.PARTITION_ID, () -> null, Integer.class);
    }

    public Object getMessageKey(final Exchange exchange) {
        return getOption(exchange, VertxKafkaConstants.MESSAGE_KEY, () -> null, Object.class);
    }

    public String getKeySerializer(final Exchange exchange) {
        return configuration.getKeySerializer();
    }

    public String getValueSerializer(final Exchange exchange) {
        return configuration.getValueSerializer();
    }

    public String getTopic(final Exchange exchange) {
        return configuration.getTopic();
    }

    public VertxKafkaConfiguration getConfiguration() {
        return configuration;
    }

    private <R> R getOption(
            final Exchange exchange, final String headerName, final Supplier<R> fallbackFn, final Class<R> type) {
        // we first try to look if our value in exchange otherwise fallback to fallbackFn which could be either a function or constant
        return ObjectHelper.isEmpty(exchange) || ObjectHelper.isEmpty(getObjectFromHeaders(exchange, headerName, type))
                ? fallbackFn.get()
                : getObjectFromHeaders(exchange, headerName, type);
    }

    private <T> T getObjectFromHeaders(final Exchange exchange, final String headerName, final Class<T> classType) {
        return exchange.getIn().getHeader(headerName, classType);
    }
}
