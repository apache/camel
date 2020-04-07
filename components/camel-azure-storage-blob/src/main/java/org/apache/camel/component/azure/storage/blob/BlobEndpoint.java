package org.apache.camel.component.azure.storage.blob;

import com.azure.storage.blob.BlobServiceClient;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.azure.storage.blob.client.BlobClientFactory;
import org.apache.camel.component.azure.storage.blob.operations.BlobOperationResponse;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The azure-storage-blob component is used for storing and retrieving blobs from Azure Storage Blob Service using SDK v12.
 */
@UriEndpoint(firstVersion = "3.2.0", scheme = "azure-storage-blob", title = "Azure Storage Blob Service", syntax = "azure-storage-blob:containerName", label = "cloud,file")
public class BlobEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(BlobEndpoint.class);

    @UriParam
    private BlobServiceClient blobServiceClient;

    @UriParam
    private BlobConfiguration configuration;

    public BlobEndpoint(final String uri, final Component component, final BlobConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new BlobProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new BlobConsumer(this, processor);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        blobServiceClient = configuration.getServiceClient() != null ? configuration.getServiceClient() : BlobClientFactory.createBlobServiceClient(configuration);
    }

    public Exchange createExchange(final BlobOperationResponse response) {
        final Exchange exchange = createExchange();
        final Message message = exchange.getIn();

        message.setHeaders(response.getHeaders());
        message.setBody(response.getBody());

        return exchange;
    }

    public void setResponseOnExchange(final BlobOperationResponse response, final Exchange exchange) {
        final Message message = exchange.getIn();

        message.setBody(response.getBody());
        message.setHeaders(response.getHeaders());
    }

    public BlobConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(BlobConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * client
     */
    public BlobServiceClient getBlobServiceClient() {
        return blobServiceClient;
    }

    public void setBlobServiceClient(BlobServiceClient blobServiceClient) {
        this.blobServiceClient = blobServiceClient;
    }
}
