package org.apache.camel.component.google.storage.client;

import org.apache.camel.component.google.storage.GoogleCloudStorageComponentConfiguration;

public class StorageInternalClientFactory {

    public static StorageInternalClient getStorageClient(GoogleCloudStorageComponentConfiguration conf) {
        return new StorageInternalClientImpl(conf);
    }

}
