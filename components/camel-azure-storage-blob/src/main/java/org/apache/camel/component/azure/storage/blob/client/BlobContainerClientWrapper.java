package org.apache.camel.component.azure.storage.blob.client;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.azure.core.http.HttpHeaders;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.models.PublicAccessType;

public class BlobContainerClientWrapper {

    private final BlobContainerClient client;

    public BlobContainerClientWrapper(final BlobContainerClient client) {
        this.client = client;
    }

    public HttpHeaders createContainer(final Map<String, String> metadata, final PublicAccessType publicAccessType, final Duration timeout) {
        return client.createWithResponse(metadata, publicAccessType, timeout, Context.NONE).getHeaders();
    }

    public HttpHeaders deleteContainer(final BlobRequestConditions blobRequestConditions, final Duration timeout) {
        return client.deleteWithResponse(blobRequestConditions, timeout, Context.NONE).getHeaders();
    }

    public List<BlobItem> listBlobs(final ListBlobsOptions listBlobsOptions, final Duration timeout) {
        return client.listBlobs(listBlobsOptions, timeout).stream().collect(Collectors.toList());
    }
}
