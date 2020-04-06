package org.apache.camel.component.azure.storage.blob.operations;

import java.time.Duration;

import com.azure.storage.blob.models.ListBlobContainersOptions;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.blob.BlobConfiguration;
import org.apache.camel.component.azure.storage.blob.BlobExchangeHeaders;
import org.apache.camel.component.azure.storage.blob.client.BlobServiceClientWrapper;
import org.apache.camel.util.ObjectHelper;

/**
 * Operations related to {@link com.azure.storage.blob.BlobServiceClient}. This is at the service level.
 */
public class BlobServiceOperations {

    private final BlobConfiguration configuration;
    private final BlobServiceClientWrapper client;

    public BlobServiceOperations(final BlobConfiguration configuration, final BlobServiceClientWrapper client) {
        ObjectHelper.notNull(client, "client cannot be null");

        this.configuration = configuration;
        this.client = client;
    }

    public BlobOperationResponse listBlobContainers(final Exchange exchange) {
        if (exchange == null) {
            return new BlobOperationResponse(client.listBlobContainers(null, null));
        }
        final ListBlobContainersOptions listBlobContainersOptions = BlobExchangeHeaders.getListBlobContainersOptionsFromHeaders(exchange);
        final Duration timeout = BlobExchangeHeaders.getTimeoutFromHeaders(exchange);

        return new BlobOperationResponse(client.listBlobContainers(listBlobContainersOptions, timeout));
    }
}
