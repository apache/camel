package org.apache.camel.component.azure.storage.blob.client;

import java.util.List;
import java.util.stream.Collectors;

import com.azure.core.http.HttpHeaders;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;

public class BlobContainerClientWrapper {

    private final BlobContainerClient client;

    public BlobContainerClientWrapper(final BlobContainerClient client) {
        this.client = client;
    }

    public HttpHeaders createContainer() {
        return client.createWithResponse(null, null, null, Context.NONE).getHeaders();
    }

    public HttpHeaders deleteContainer() {
        return client.deleteWithResponse(null, null, Context.NONE).getHeaders();
    }

    public List<BlobItem> listBlobs() {
        return client.listBlobs().stream().collect(Collectors.toList());
    }
}
