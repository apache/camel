package org.apache.camel.component.azure.eventhubs;

import java.util.function.Supplier;

import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;

/**
 * A proxy class for {@link EventHubsConfiguration} and {@link EventHubsConstants}. Ideally this
 * is responsible to obtain the correct configurations options either from configs or exchange headers
 */
public class EventHubsConfigurationOptionsProxy {

    private final EventHubsConfiguration configuration;

    public EventHubsConfigurationOptionsProxy(final EventHubsConfiguration configuration) {
        this.configuration = configuration;
    }

    public String getPartitionKey(final Exchange exchange) {
        return getOption(exchange, EventHubsConstants.PARTITION_KEY, configuration::getPartitionKey, String.class);
    }

    public String getPartitionId(final Exchange exchange) {
        return getOption(exchange, EventHubsConstants.PARTITION_ID, configuration::getPartitionId, String.class);
    }

    public EventHubsConfiguration getConfiguration() {
        return configuration;
    }

    private <R> R getOption(final Exchange exchange, final String headerName, final Supplier<R> fallbackFn, final Class<R> type) {
        // we first try to look if our value in exchange otherwise fallback to fallbackFn which could be either a function or constant
        return ObjectHelper.isEmpty(exchange) || ObjectHelper.isEmpty(getObjectFromHeaders(exchange, headerName, type)) ? fallbackFn.get()
                : getObjectFromHeaders(exchange, headerName, type);
    }

    private static <T> T getObjectFromHeaders(final Exchange exchange, final String headerName, final Class<T> classType) {
        return exchange.getIn().getHeader(headerName, classType);
    }

}
