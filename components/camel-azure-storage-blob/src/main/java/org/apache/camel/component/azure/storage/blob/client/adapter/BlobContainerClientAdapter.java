package org.apache.camel.component.azure.storage.blob.client.adapter;

import java.util.List;

import com.azure.core.http.HttpHeaders;
import com.azure.storage.blob.models.BlobItem;

public interface BlobContainerClientAdapter {
    HttpHeaders createContainer();

    HttpHeaders deleteContainer();

    List<BlobItem> listBlobs();
}
