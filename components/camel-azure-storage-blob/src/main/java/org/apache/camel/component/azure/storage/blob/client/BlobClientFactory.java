package org.apache.camel.component.azure.storage.blob.client;

import java.util.Locale;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.apache.camel.component.azure.storage.blob.BlobConfiguration;
import org.apache.camel.util.ObjectHelper;

public final class BlobClientFactory {

    private static final String SERVICE_URI_SEGMENT = ".blob.core.windows.net";

    public static BlobServiceClient createBlobServiceClient(final BlobConfiguration configuration) {
        return new BlobServiceClientBuilder()
                .endpoint(buildAzureEndpointUri(configuration))
                .credential(getCredentialForClient(configuration))
                .buildClient();
    }

    private static String buildAzureEndpointUri(final BlobConfiguration configuration) {
        return String.format(Locale.ROOT, "https://%s" + SERVICE_URI_SEGMENT, configuration.getAccountName());
    }

    private static StorageSharedKeyCredential getCredentialForClient(final BlobConfiguration configuration) {
        final StorageSharedKeyCredential storageSharedKeyCredential = configuration.getStorageSharedKeyCredential();

        if (storageSharedKeyCredential != null) {
            return storageSharedKeyCredential;
        }

        return new StorageSharedKeyCredential(configuration.getAccountName(), configuration.getAccessKey());
    }

}
