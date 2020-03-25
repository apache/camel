package org.apache.camel.component.azure.storage.blob.client;

import java.util.LinkedList;
import java.util.List;

import com.azure.core.http.HttpHeaders;
import com.azure.storage.blob.models.BlobItem;
import org.apache.camel.component.azure.storage.blob.client.adapter.BlobContainerClientAdapter;

public class BlobContainerClientAdapterMockImpl implements BlobContainerClientAdapter {
    @Override
    public HttpHeaders createContainer() {
        final HttpHeaders httpHeaders = new HttpHeaders();

        httpHeaders.put("x-ms-request-id", "12345");
        httpHeaders.put("Server", "Azure-Server");

        return httpHeaders;
    }

    @Override
    public HttpHeaders deleteContainer() {
        final HttpHeaders httpHeaders = new HttpHeaders();

        httpHeaders.put("x-ms-request-id", "12345");
        httpHeaders.put("Server", "Azure-Server");
        httpHeaders.put("x-ms-delete-type-permanent", "true");

        return httpHeaders;
    }

    @Override
    public List<BlobItem> listBlobs() {
        final List<BlobItem> items = new LinkedList<>();

        final BlobItem blobItem1 = new BlobItem().setName("item-1").setVersionId("1").setDeleted(false);
        final BlobItem blobItem2 = new BlobItem().setName("item-2").setVersionId("2").setDeleted(true);

        items.add(blobItem1);
        items.add(blobItem2);

        return items;
    }
}
