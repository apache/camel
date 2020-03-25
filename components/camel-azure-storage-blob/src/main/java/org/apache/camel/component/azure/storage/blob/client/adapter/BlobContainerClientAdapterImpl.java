package org.apache.camel.component.azure.storage.blob.client.adapter;

import java.util.List;
import java.util.stream.Collectors;

import com.azure.core.http.HttpHeaders;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;

public class BlobContainerClientAdapterImpl implements BlobContainerClientAdapter {

    private final BlobContainerClient client;

    public BlobContainerClientAdapterImpl(final BlobContainerClient client) {
        this.client = client;
    }

    @Override
    public HttpHeaders createContainer() {
        return client.createWithResponse(null, null, null, Context.NONE).getHeaders();
    }

    @Override
    public HttpHeaders deleteContainer() {
        return client.deleteWithResponse(null, null, Context.NONE).getHeaders();
    }

    @Override
    public List<BlobItem> listBlobs() {
        return client.listBlobs().stream().collect(Collectors.toList());
    }
}
