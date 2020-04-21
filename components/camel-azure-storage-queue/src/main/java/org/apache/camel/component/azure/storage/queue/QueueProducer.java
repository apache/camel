package org.apache.camel.component.azure.storage.queue;

import com.azure.storage.queue.QueueServiceClient;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.queue.client.QueueServiceClientWrapper;
import org.apache.camel.component.azure.storage.queue.operations.QueueOperationResponse;
import org.apache.camel.component.azure.storage.queue.operations.QueueOperations;
import org.apache.camel.component.azure.storage.queue.operations.QueueServiceOperations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

/**
 * A Producer which sends messages to the Azure Storage Queue Service
 */
public class QueueProducer extends DefaultProducer {

    private final QueueConfiguration configuration;
    private final QueueServiceClientWrapper queueServiceClientWrapper;
    private final QueueServiceOperations queueServiceOperations;

    public QueueProducer(final Endpoint endpoint) {
        super(endpoint);
        this.configuration = getEndpoint().getConfiguration();
        this.queueServiceClientWrapper = new QueueServiceClientWrapper(getEndpoint().getQueueServiceClient());
        this.queueServiceOperations = new QueueServiceOperations(queueServiceClientWrapper);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        QueueOperationDefinition operation = determineOperation(exchange);

        if (ObjectHelper.isEmpty(operation)) {
            operation = QueueOperationDefinition.sendMessage;
        }

        switch (operation) {
            // service operations
            case listQueues:
                setResponse(exchange, queueServiceOperations.listQueues(exchange));
                break;
            // queue operations
            case createQueue:
                setResponse(exchange, getQueueOperations(exchange).createQueue(exchange));
                break;
            case deleteQueue:
                setResponse(exchange, getQueueOperations(exchange).deleteQueue(exchange));
                break;
            case clearQueue:
                setResponse(exchange, getQueueOperations(exchange).clearQueue(exchange));
                break;
            case sendMessage:
                setResponse(exchange, getQueueOperations(exchange).sendMessage(exchange));
                break;
            case deleteMessage:
                setResponse(exchange, getQueueOperations(exchange).deleteMessage(exchange));
                break;
            case peekMessages:
                setResponse(exchange, getQueueOperations(exchange).peekMessages(exchange));
                break;
            case updateMessage:
                setResponse(exchange, getQueueOperations(exchange).updateMessage(exchange));
                break;
            case receiveMessages:
                setResponse(exchange, getQueueOperations(exchange).receiveMessages(exchange));
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    @Override
    public QueueEndpoint getEndpoint() {
        return (QueueEndpoint) super.getEndpoint();
    }

    private void setResponse(final Exchange exchange, final QueueOperationResponse response) {
        exchange.getMessage().setBody(response.getBody());
        exchange.getMessage().setHeaders(response.getHeaders());
    }

    private QueueOperationDefinition determineOperation(final Exchange exchange) {
        final QueueOperationDefinition operation = QueueExchangeHeaders.getQueueOperationsDefinitionFromHeaders(exchange);
        if (operation != null) {
            return operation;
        }
        return configuration.getOperation();
    }

    private QueueOperations getQueueOperations(final Exchange exchange) {
        return new QueueOperations(configuration, queueServiceClientWrapper.getQueueClientWrapper(determineQueueName(exchange)));
    }

    public String determineQueueName(final Exchange exchange) {
        final String queueName = ObjectHelper.isEmpty(QueueExchangeHeaders.getQueueNameFromHeaders(exchange)) ? configuration.getQueueName()
                : QueueExchangeHeaders.getQueueNameFromHeaders(exchange);

        if (ObjectHelper.isEmpty(queueName)) {
            throw new IllegalArgumentException("Queue name must be specified");
        }
        return queueName;
    }
}
