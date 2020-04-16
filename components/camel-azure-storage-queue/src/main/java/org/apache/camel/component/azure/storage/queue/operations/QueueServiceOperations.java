package org.apache.camel.component.azure.storage.queue.operations;

import java.time.Duration;

import com.azure.storage.queue.models.QueuesSegmentOptions;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.queue.QueueExchangeHeaders;
import org.apache.camel.component.azure.storage.queue.client.QueueServiceClientWrapper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * All operations related to {@link com.azure.storage.queue.QueueServiceClient}. This is at the service level
 */
public class QueueServiceOperations {

    private static final Logger LOG = LoggerFactory.getLogger(QueueServiceOperations.class);

    private QueueServiceClientWrapper client;

    public QueueServiceOperations(final QueueServiceClientWrapper client) {
        ObjectHelper.notNull(client, "client can not be null.");

        this.client = client;
    }

    public QueueOperationResponse listQueues(final Exchange exchange) {
        if (exchange == null) {
            return new QueueOperationResponse(client.listQueues(null, null));
        }
        final QueuesSegmentOptions segmentOptions = QueueExchangeHeaders.getQueuesSegmentOptionsFromHeaders(exchange);
        final Duration timeout = QueueExchangeHeaders.getTimeoutFromHeaders(exchange);

        return new QueueOperationResponse(client.listQueues(segmentOptions, timeout));
    }
}
