package org.apache.camel.component.azure.storage.queue.client;

import java.util.Locale;

import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.queue.QueueServiceClient;
import com.azure.storage.queue.QueueServiceClientBuilder;
import org.apache.camel.component.azure.storage.queue.QueueConfiguration;
import org.apache.camel.util.ObjectHelper;

public class QueueClientFactory {

    private static final String SERVICE_URI_SEGMENT = ".queue.core.windows.net";

    private QueueClientFactory() {
    }

    public static QueueServiceClient createQueueServiceClient(final QueueConfiguration configuration) {
        return new QueueServiceClientBuilder()
                .endpoint(buildAzureEndpointUri(configuration))
                .credential(getCredentialForClient(configuration))
                .buildClient();
    }

    private static String buildAzureEndpointUri(final QueueConfiguration configuration) {
        return String.format(Locale.ROOT, "https://%s" + SERVICE_URI_SEGMENT, getAccountName(configuration));
    }

    private static StorageSharedKeyCredential getCredentialForClient(final QueueConfiguration configuration) {
        final StorageSharedKeyCredential storageSharedKeyCredential = configuration.getCredentials();

        if (storageSharedKeyCredential != null) {
            return storageSharedKeyCredential;
        }

        return new StorageSharedKeyCredential(configuration.getAccountName(), configuration.getAccessKey());
    }

    private static String getAccountName(final QueueConfiguration configuration) {
        return !ObjectHelper.isEmpty(configuration.getCredentials()) ? configuration.getCredentials().getAccountName() : configuration.getAccountName();
    }
}
