package org.apache.camel.component.azure.storage.queue.operations;

import java.time.Duration;
import java.util.Map;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.queue.QueueConfiguration;
import org.apache.camel.component.azure.storage.queue.QueueExchangeHeaders;
import org.apache.camel.component.azure.storage.queue.client.QueueClientWrapper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * All operations related to {@link com.azure.storage.queue.QueueClient}. This is at the queue level
 */
public class QueueOperations {

    private static final Logger LOG = LoggerFactory.getLogger(QueueOperations.class);

    private final QueueConfiguration configuration;
    private final QueueClientWrapper client;

    public QueueOperations(final QueueConfiguration configuration, final QueueClientWrapper client) {
        ObjectHelper.notNull(client, "client can not be null.");

        this.configuration = configuration;
        this.client = client;
    }

    public QueueOperationResponse createQueue(final Exchange exchange) {
        if (exchange == null) {
            return buildResponseWithEmptyBody(client.create(null, null));
        }

        final Map<String, String> metadata = QueueExchangeHeaders.getMetadataFromHeaders(exchange);
        final Duration timeout = QueueExchangeHeaders.getTimeoutFromHeaders(exchange);

        return buildResponseWithEmptyBody(client.create(metadata, timeout));
    }

    public QueueOperationResponse clearQueue(final Exchange exchange) {
        if (exchange == null) {
            return buildResponseWithEmptyBody(client.clearMessages(null));
        }

        final Duration timeout = QueueExchangeHeaders.getTimeoutFromHeaders(exchange);

        return buildResponseWithEmptyBody(client.clearMessages(timeout));
    }

    public QueueOperationResponse deleteQueue(final Exchange exchange) {
        if (exchange == null) {
            return buildResponseWithEmptyBody(client.delete(null));
        }

        final Duration timeout = QueueExchangeHeaders.getTimeoutFromHeaders(exchange);

        return buildResponseWithEmptyBody(client.delete(timeout));
    }

    public QueueOperationResponse sendMessage(final Exchange exchange) {
        ObjectHelper.notNull(exchange, "exchange cannot be null");

        final boolean queueCreated = QueueExchangeHeaders.getQueueCreatedFlagFromHeaders(exchange);

        if (!queueCreated) {
            createQueue(exchange);
        }

        final String text = QueueExchangeHeaders.getMessageTextFromHeaders(exchange);
        final Duration visibilityTimeout = getVisibilityTimeout(exchange);
        final Duration timeToLive = getTimeToLive(exchange);
        final Duration timeout = QueueExchangeHeaders.getTimeoutFromHeaders(exchange);

        return buildResponseWithEmptyBody(client.sendMessage(text, visibilityTimeout, timeToLive, timeout));
    }

    @SuppressWarnings("rawtypes")
    private QueueOperationResponse buildResponseWithEmptyBody(final Response response) {
        return buildResponse(response, true);
    }

    @SuppressWarnings("rawtypes")
    private QueueOperationResponse buildResponseWithBody(final Response response) {
        return buildResponse(response, false);
    }

    @SuppressWarnings("rawtypes")
    private QueueOperationResponse buildResponse(final Response response, final boolean emptyBody) {
        final Object body = emptyBody ? true : response.getValue();
        QueueExchangeHeaders exchangeHeaders;

        if (response.getValue() instanceof SendMessageResult) {
            exchangeHeaders = QueueExchangeHeaders.createQueueExchangeHeadersFromSendMessageResult((SendMessageResult) response.getValue());
        } else {
            exchangeHeaders = new QueueExchangeHeaders();
        }

        exchangeHeaders.httpHeaders(response.getHeaders());

        return new QueueOperationResponse(body, exchangeHeaders.toMap());
    }

    private Duration getVisibilityTimeout(final Exchange exchange) {
        return ObjectHelper.isEmpty(QueueExchangeHeaders.getVisibilityTimeout(exchange)) ? configuration.getVisibilityTimeout()
                : QueueExchangeHeaders.getVisibilityTimeout(exchange);
    }

    private Duration getTimeToLive(final Exchange exchange) {
        return ObjectHelper.isEmpty(QueueExchangeHeaders.getTimeToLiveFromHeaders(exchange)) ? configuration.getTimeToLive()
                : QueueExchangeHeaders.getTimeToLiveFromHeaders(exchange);
    }

}
