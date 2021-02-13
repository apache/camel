package org.apache.camel.component.google.storage;

import java.io.FileInputStream;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

public class GoogleCloudStorageConnectionFactory {

    public static Storage create(GoogleCloudStorageComponentConfiguration configuration) throws Exception {
        Storage storage = StorageOptions.newBuilder()
                .setCredentials(ServiceAccountCredentials.fromStream(new FileInputStream(configuration.getServiceAccountKey())))
                .build().getService();
        return storage;
    }

}
