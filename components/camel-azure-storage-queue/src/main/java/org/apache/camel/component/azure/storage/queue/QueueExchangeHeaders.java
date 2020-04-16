package org.apache.camel.component.azure.storage.queue;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.azure.storage.queue.models.QueuesSegmentOptions;
import org.apache.camel.Exchange;

public class QueueExchangeHeaders {

    private final Map<String, Object> headers = new HashMap<>();

    public static QueuesSegmentOptions getQueuesSegmentOptionsFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, QueueConstants.QUEUES_SEGMENT_OPTIONS, QueuesSegmentOptions.class);
    }

    public static Duration getTimeoutFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, QueueConstants.TIMEOUT, Duration.class);
    }

    private static <T> T getObjectFromHeaders(final Exchange exchange, final String headerName, final Class<T> classType) {
        return exchange.getIn().getHeader(headerName, classType);
    }
}
