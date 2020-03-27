package org.apache.camel.component.azure.storage.blob.client;

import java.util.List;
import java.util.stream.Collectors;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobContainerItem;
import org.apache.camel.util.ObjectHelper;

public class BlobServiceClientWrapper {

    private final BlobServiceClient client;

    public BlobServiceClientWrapper(final BlobServiceClient client) {
        ObjectHelper.notNull(client, "client cannot be null");

        this.client = client;
    }

    public List<BlobContainerItem> listBlobContainers() {
        return client.listBlobContainers().stream().collect(Collectors.toList());
    }

    public BlobContainerClientWrapper getBlobContainerClientWrapper(final String containerName) {
        if (!ObjectHelper.isEmpty(containerName)) {
            return new BlobContainerClientWrapper(client.getBlobContainerClient(containerName));
        }
        throw new IllegalArgumentException("Cannot initialize a blob container since no container name was provided.");
    }
}
