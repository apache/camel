package org.apache.camel.component.azure.storage.blob.operations;

import org.apache.camel.component.azure.storage.blob.BlobConfiguration;
import org.apache.camel.component.azure.storage.blob.client.adapter.BlobContainerClientAdapter;
import org.apache.camel.util.ObjectHelper;

/**
 * All operations related to {@link com.azure.storage.blob.BlobContainerClient}
 */
public class BlobContainerOperations {

    private final BlobConfiguration configuration;
    private final BlobContainerClientAdapter client;

    public BlobContainerOperations(final BlobConfiguration configuration, final BlobContainerClientAdapter client) {
        this.configuration = configuration;
        this.client = client;
    }

    public BlobOperationResponse listBlobs() {
        checkIfContainerNameIsEmpty(configuration);

        final BlobOperationResponse blobOperationResponse = new BlobOperationResponse();
        blobOperationResponse.setBody(client.listBlobs());

        return blobOperationResponse;
    }

    public BlobOperationResponse createContainer() {
        checkIfContainerNameIsEmpty(configuration);

        final BlobOperationResponse blobOperationResponse = new BlobOperationResponse();

        blobOperationResponse.setHeaders(client.createContainer());
        blobOperationResponse.setBody(true);

        return blobOperationResponse;
    }

    public BlobOperationResponse deleteContainer() {
        checkIfContainerNameIsEmpty(configuration);

        final BlobOperationResponse blobOperationResponse = new BlobOperationResponse();

        blobOperationResponse.setHeaders(client.deleteContainer());
        blobOperationResponse.setBody(true);

        return blobOperationResponse;
    }

    private void checkIfContainerNameIsEmpty(final BlobConfiguration configuration) {
        // we need to have a container to list blobs
        if (ObjectHelper.isEmpty(configuration.getContainerName())) {
            throw new IllegalArgumentException("No container name was specified while on ListBlobs operations.");
        }
    }
}
