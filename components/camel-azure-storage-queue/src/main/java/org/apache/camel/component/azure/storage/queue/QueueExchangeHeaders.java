package org.apache.camel.component.azure.storage.queue;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import com.azure.core.http.HttpHeaders;
import com.azure.storage.queue.models.QueuesSegmentOptions;
import com.azure.storage.queue.models.SendMessageResult;
import org.apache.camel.Exchange;

public class QueueExchangeHeaders {

    private final Map<String, Object> headers = new HashMap<>();

    public static QueueExchangeHeaders createQueueExchangeHeadersFromSendMessageResult(final SendMessageResult result) {
        return new QueueExchangeHeaders()
                .messageId(result.getMessageId())
                .insertionTime(result.getInsertionTime())
                .expirationTime(result.getExpirationTime())
                .popReceipt(result.getPopReceipt())
                .timeNextVisible(result.getTimeNextVisible());
    }

    public Map<String, Object> toMap() {
        return headers;
    }

    public static QueuesSegmentOptions getQueuesSegmentOptionsFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, QueueConstants.QUEUES_SEGMENT_OPTIONS, QueuesSegmentOptions.class);
    }

    public static Duration getTimeoutFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, QueueConstants.TIMEOUT, Duration.class);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> getMetadataFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, QueueConstants.METADATA, Map.class);
    }

    public static String getMessageTextFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, QueueConstants.MESSAGE_TEXT, String.class);
    }

    public static Duration getTimeToLiveFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, QueueConstants.TIME_TO_LIVE, Duration.class);
    }

    public static Duration getVisibilityTimeout(final Exchange exchange) {
        return getObjectFromHeaders(exchange, QueueConstants.VISIBILITY_TIMEOUT, Duration.class);
    }

    public static boolean getQueueCreatedFlagFromHeaders(final Exchange exchange) {
        return getObjectFromHeaders(exchange, QueueConstants.QUEUE_CREATED, boolean.class);
    }

    private static <T> T getObjectFromHeaders(final Exchange exchange, final String headerName, final Class<T> classType) {
        return exchange.getIn().getHeader(headerName, classType);
    }

    public QueueExchangeHeaders messageId(final String id) {
        headers.put(QueueConstants.MESSAGE_ID, id);
        return this;
    }

    public QueueExchangeHeaders insertionTime(final OffsetDateTime insertionTime) {
        headers.put(QueueConstants.INSERTION_TIME, insertionTime);
        return this;
    }

    public QueueExchangeHeaders expirationTime(final OffsetDateTime expirationTime) {
        headers.put(QueueConstants.EXPIRATION_TIME, expirationTime);
        return this;
    }

    public QueueExchangeHeaders popReceipt(final String pop) {
        headers.put(QueueConstants.POP_RECEIPT, pop);
        return this;
    }

    public QueueExchangeHeaders timeNextVisible(final OffsetDateTime timeNextVisible) {
        headers.put(QueueConstants.TIME_NEXT_VISIBLE, timeNextVisible);
        return this;
    }

    public QueueExchangeHeaders httpHeaders(final HttpHeaders httpHeaders) {
        headers.put(QueueConstants.RAW_HTTP_HEADERS, httpHeaders);
        return this;
    }
}
