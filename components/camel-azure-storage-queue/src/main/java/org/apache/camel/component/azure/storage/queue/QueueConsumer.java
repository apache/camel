package org.apache.camel.component.azure.storage.queue;

import com.azure.storage.queue.QueueServiceClient;
import com.azure.storage.queue.models.QueueStorageException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.azure.storage.queue.client.QueueClientWrapper;
import org.apache.camel.component.azure.storage.queue.operations.QueueOperationResponse;
import org.apache.camel.component.azure.storage.queue.operations.QueueOperations;
import org.apache.camel.support.ScheduledPollConsumer;

public class QueueConsumer extends ScheduledPollConsumer {

    public QueueConsumer(final QueueEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected int poll() throws Exception {
        final String queueName = getEndpoint().getConfiguration().getQueueName();
        final QueueServiceClient serviceClient = getEndpoint().getQueueServiceClient();
        final QueueClientWrapper clientWrapper = new QueueClientWrapper(serviceClient.getQueueClient(queueName));
        final QueueOperations operations = new QueueOperations(getEndpoint().getConfiguration(), clientWrapper);
        final Exchange exchange = getEndpoint().createExchange();

        try {
            final QueueOperationResponse response = operations.receiveMessages(null);
            getEndpoint().setResponseOnExchange(response, exchange);

            getAsyncProcessor().process(exchange);
            return 1;
        } catch (QueueStorageException ex) {
            if (404 == ex.getStatusCode()) {
                return 0;
            } else {
                throw ex;
            }
        }
    }

    @Override
    public QueueEndpoint getEndpoint() {
        return (QueueEndpoint) super.getEndpoint();
    }
}
