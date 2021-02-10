package org.apache.camel.component.google.storage.client;

import java.io.FileInputStream;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.apache.camel.component.google.storage.GoogleCloudStorageComponentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StorageInternalClientImpl implements StorageInternalClient {

    private final static Logger LOGGER = LoggerFactory.getLogger(StorageInternalClientImpl.class);

    private GoogleCloudStorageComponentConfiguration configuration;

    public StorageInternalClientImpl(GoogleCloudStorageComponentConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Storage getGoogleCloudStorage() {

        try {
            LOGGER.info("creating GoogleCloudStorage client using aplicationCredentials: {}",
                    configuration.getServiceAccountCredentials());
            FileInputStream serviceAccountCrediantialsFIS = new FileInputStream(configuration.getServiceAccountCredentials());

            Storage storage = StorageOptions.newBuilder()
                    .setCredentials(ServiceAccountCredentials.fromStream(serviceAccountCrediantialsFIS))
                    // .setProjectId(projectId)
                    .build().getService();

            return storage;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

}
