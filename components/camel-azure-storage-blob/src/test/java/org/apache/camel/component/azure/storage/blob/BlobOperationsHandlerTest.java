package org.apache.camel.component.azure.storage.blob;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobDownloadResponse;
import org.apache.camel.component.azure.storage.blob.client.BlobClientFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlobOperationsHandlerTest {

    BlobConfiguration configuration;

    @BeforeAll
    public void setup() {
        configuration = new BlobConfiguration();
        configuration.setAccountName("cameldev");
        configuration.setAccessKey("");
    }

    @Test
    public void testHandleListBlobContainers() {
        final BlobServiceClient client = BlobClientFactory.createBlobServiceClient(configuration);
        final BlobOperationsHandler handler = new BlobOperationsHandler(configuration);

        handler.handleListBlobContainers(client).forEach(blobContainerItem -> System.out.println(blobContainerItem.getName()));
    }


    @Test
    public void testHandleListBlobs() {
        configuration.setContainerName("test");

        final BlobContainerClient client = BlobClientFactory.createBlobContainerClient(configuration);
        final BlobOperationsHandler handler = new BlobOperationsHandler(configuration);

        handler.handleListBlobs(client).forEach(blobItem -> System.out.println(blobItem.getName()));
    }

    @Test
    public void testHandleGetBlob() {
        configuration.setContainerName("test");
        configuration.setBlobName("0b4e673827795_1_V550.jpg");

        final BlobClient client = BlobClientFactory.createBlobClient(configuration);
        final BlobOperationsHandler handler = new BlobOperationsHandler(configuration);

        final BlobDownloadResponse downloadResponse = handler.handleDownloadBlob(client);

        System.out.println(downloadResponse.getHeaders());
    }
}