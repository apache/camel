package org.apache.camel.component.azure.storage.blob.operations;

import org.apache.camel.component.azure.storage.blob.BlobConfiguration;
import org.apache.camel.component.azure.storage.blob.client.BlobServiceClientWrapper;
import org.apache.camel.util.ObjectHelper;

/**
 * Operations related to {@link com.azure.storage.blob.BlobServiceClient}
 */
public class BlobServiceOperations {

    private final BlobConfiguration configuration;
    private final BlobServiceClientWrapper client;

    public BlobServiceOperations(final BlobConfiguration configuration, final BlobServiceClientWrapper client) {
        ObjectHelper.notNull(client, "client cannot be null");

        this.configuration = configuration;
        this.client = client;
    }

    public BlobOperationResponse listBlobContainers() {
        final BlobOperationResponse blobOperationResponse = new BlobOperationResponse();
        blobOperationResponse.setBody(client.listBlobContainers());

        return blobOperationResponse;
    }
}
