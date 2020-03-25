package org.apache.camel.component.azure.storage.blob.operations;

import java.util.stream.Collectors;

import com.azure.storage.blob.BlobServiceClient;
import org.apache.camel.component.azure.storage.blob.BlobConfiguration;

/**
 * Operations related to {@link com.azure.storage.blob.BlobServiceClient}
 */
public class BlobServiceOperations {

    private final BlobConfiguration configuration;
    private final BlobServiceClient client;

    public BlobServiceOperations(final BlobConfiguration configuration, final BlobServiceClient client) {
        this.configuration = configuration;
        this.client = client;
    }

    public BlobOperationResponse listBlobContainers() {
        final BlobOperationResponse blobOperationResponse = new BlobOperationResponse();
        blobOperationResponse.setBody(client.listBlobContainers().stream()
                .collect(Collectors.toList()));

        return blobOperationResponse;
    }
}
