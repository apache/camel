package org.apache.camel.component.google.storage.client;

import com.google.cloud.storage.Storage;

public interface StorageInternalClient {
    Storage getGoogleCloudStorage();
}
