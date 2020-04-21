package org.apache.camel.component.azure.storage.queue;

import com.azure.storage.queue.QueueServiceClient;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.azure.storage.queue.client.QueueClientFactory;
import org.apache.camel.component.azure.storage.queue.operations.QueueOperationResponse;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * The azure-storage-queue component is used for storing and retrieving the messages to/from Azure Storage Queue using Azure SDK v12.
 */
@UriEndpoint(firstVersion = "3.3.0", scheme = "azure-storage-queue", title = "Azure Storage Queue Service", syntax = "azure-storage-queue:queueName", label = "cloud,messaging")
public class QueueEndpoint extends DefaultEndpoint {

    @UriParam
    private QueueServiceClient queueServiceClient;

    @UriParam
    private QueueConfiguration configuration;

    public QueueEndpoint(final String uri, final Component component, final QueueConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new QueueProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        if (ObjectHelper.isEmpty(configuration.getQueueName())) {
            throw new IllegalArgumentException("QueueName must be set.");
        }
        return new QueueConsumer(this, processor);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        queueServiceClient = configuration.getServiceClient() != null ? configuration.getServiceClient() : QueueClientFactory.createQueueServiceClient(configuration);
    }

    public void setResponseOnExchange(final QueueOperationResponse response, final Exchange exchange) {
        final Message message = exchange.getIn();

        message.setBody(response.getBody());
        message.setHeaders(response.getHeaders());
    }

    /**
     * The component configurations
     */
    public QueueConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(QueueConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Service client to a storage account to interact with the queue service. This client does not hold any state about a particular storage account
     * but is instead a convenient way of sending off appropriate requests to the resource on the service.
     * It may also be used to construct URLs to blobs and containers.
     *
     * This client contains all the operations for interacting with a queue account in Azure Storage.
     * Operations allowed by the client are creating, listing, and deleting queues, retrieving and updating properties of
     * the account, and retrieving statistics of the account.
     */
    public QueueServiceClient getQueueServiceClient() {
        return queueServiceClient;
    }

    public void setQueueServiceClient(QueueServiceClient queueServiceClient) {
        this.queueServiceClient = queueServiceClient;
    }
}
