package org.apache.camel.component.azure.storage.blob;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.azure.storage.blob.client.BlobContainerClientWrapper;
import org.apache.camel.component.azure.storage.blob.client.BlobServiceClientWrapper;
import org.apache.camel.component.azure.storage.blob.operations.BlobContainerOperations;
import org.apache.camel.component.azure.storage.blob.operations.BlobOperationResponse;
import org.apache.camel.component.azure.storage.blob.operations.BlobOperations;
import org.apache.camel.component.azure.storage.blob.operations.BlobServiceOperations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Producer which sends messages to the Azure Storage Blob Service
 */
public class BlobProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(BlobProducer.class);

    private final BlobConfiguration configuration;

    private BlobServiceOperations blobServiceOperations;
    private BlobContainerOperations blobContainerOperations;

    public BlobProducer(final Endpoint endpoint, final BlobServiceClientWrapper blobServiceClientWrapper) {
        super(endpoint);
        this.configuration = getEndpoint().getConfiguration();
        this.blobServiceOperations = new BlobServiceOperations(configuration, blobServiceClientWrapper);
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        BlobOperationsDefinition operation = determineOperation(exchange);

        if (ObjectHelper.isEmpty(operation)) {
            // TODO
            operation = BlobOperationsDefinition.listBlobContainers;
        }

        switch (operation) {
            case listBlobContainers:
                setResponse(exchange, blobServiceOperations.listBlobContainers());
                break;
            case listBlobs:
                listBlobs(exchange);
                break;
        }
    }

    private void listBlobs(final Exchange exchange) {

    }

    private void getBlob(final Exchange exchange) {

    }

    private void setResponse(final Exchange exchange, final BlobOperationResponse blobOperationResponse) {
        exchange.getMessage().setBody(blobOperationResponse.getBody());
        exchange.getMessage().setHeaders(blobOperationResponse.getHeaders());
    }

    @Override
    public BlobEndpoint getEndpoint() {
        return (BlobEndpoint) super.getEndpoint();
    }

    private BlobOperationsDefinition determineOperation(final Exchange exchange) {
        BlobOperationsDefinition operation = exchange.getIn().getHeader(BlobConstants.BLOB_OPERATION, BlobOperationsDefinition.class);
        if (operation == null) {
            operation = configuration.getOperation();
        }
        return operation;
    }

    private static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }
}
