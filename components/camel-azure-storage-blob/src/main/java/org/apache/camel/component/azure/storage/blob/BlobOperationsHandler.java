package org.apache.camel.component.azure.storage.blob;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.stream.Collectors;

import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobContainerItem;
import com.azure.storage.blob.models.BlobDownloadResponse;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.specialized.BlobClientBase;
import org.apache.camel.util.ObjectHelper;

/**
 * Handler responsible executing blob operations
 */
public class BlobOperationsHandler {

    private final BlobConfiguration configuration;

    public BlobOperationsHandler(final BlobConfiguration configuration) {
        this.configuration = configuration;
    }

    public List<BlobContainerItem> handleListBlobContainers(final BlobServiceClient client) {
        return client.listBlobContainers().stream()
                .collect(Collectors.toList());
    }

    public BlobDownloadResponse handleDownloadBlob(final BlobClient client) {
       checkIfContainerOrBlobIsEmpty(configuration);

       ///client.download();

       return client.downloadWithResponse(new ByteArrayOutputStream(), null, null, null, false, null, Context.NONE);
    }

    public List<BlobItem> handleListBlobs(final BlobContainerClient client) {
        // we need to have a container to list blobs
        if (ObjectHelper.isEmpty(configuration.getContainerName())) {
            throw new IllegalArgumentException("No container name was specified while on ListBlobs operations.");
        }
        return client.listBlobs().stream()
                .collect(Collectors.toList());
    }

    private void checkIfContainerOrBlobIsEmpty(final BlobConfiguration configuration) {
        if (ObjectHelper.isEmpty(configuration.getContainerName()) || ObjectHelper.isEmpty(configuration.getBlobName())) {
            throw new IllegalArgumentException("No blob or container name was specified.");
        }
    }
}
