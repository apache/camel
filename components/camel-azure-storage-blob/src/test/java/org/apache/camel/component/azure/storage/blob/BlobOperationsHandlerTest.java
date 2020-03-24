package org.apache.camel.component.azure.storage.blob;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;

import com.azure.storage.blob.models.BlobContainerItem;
import com.azure.storage.blob.models.BlobItem;
import org.apache.camel.component.azure.storage.blob.client.BlobClientFactory;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlobOperationsHandlerTest {

    BlobConfiguration configuration;

    @BeforeAll
    public void setup() throws IOException {
        configuration = new BlobConfiguration();
        configuration.setAccountName("cameldev");
        configuration.setAccessKey(loadKey());
    }

    private String loadKey() throws IOException {
        final InputStream inputStream = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(".azure_key"));

        return IOUtils.toString(inputStream, Charset.defaultCharset());
    }

    @Test
    public void testHandleListBlobContainers() {
        final BlobServiceClient client = BlobClientFactory.createBlobServiceClient(configuration);
        final BlobOperationsHandler handler = new BlobOperationsHandler(configuration);

        final List<BlobContainerItem> blobContainerItems = (List<BlobContainerItem>) handler.handleListBlobContainers(client).getBody();

        blobContainerItems.forEach(blobContainerItem -> System.out.println(blobContainerItem.getName()));
    }


    @Test
    public void testHandleListBlobs() {
        configuration.setContainerName("test");

        final BlobContainerClient client = BlobClientFactory.createBlobContainerClient(configuration);
        final BlobOperationsHandler handler = new BlobOperationsHandler(configuration);

        final List<BlobItem> blobContainerItems = (List<BlobItem>) handler.handleListBlobs(client).getBody();

        blobContainerItems.forEach(blobItem -> System.out.println(blobItem.getName()));
    }

    @Test
    public void testHandleGetBlob() throws IOException {
        configuration.setContainerName("test");
        configuration.setBlobName("0b4e673827795_1_V550.jpg");
        configuration.setFileDir("/Users/oalsafi/Work/Apache/camel/components/camel-azure-storage-blob");

        final BlobClient client = BlobClientFactory.createBlobClient(configuration);
        final BlobOperationsHandler handler = new BlobOperationsHandler(configuration);

        //final BlobDownloadResponse downloadResponse = handler.handleDownloadBlob(client);

        //System.out.println(downloadResponse.getDeserializedHeaders());

        final BlobExchangeResponse response = handler.handleDownloadBlob(client);

        System.out.println(response.getHeaders());
    }

    @Test
    public void handleDeleteBlob() {
        configuration.setContainerName("test");
        configuration.setBlobName("Sharklets_Texture.png");

        final BlobClient client = BlobClientFactory.createBlobClient(configuration);

        final BlobOperationsHandler handler = new BlobOperationsHandler(configuration);

        final BlobExchangeResponse blobExchangeResponse = handler.handleDeleteBlob(client);

        System.out.println(blobExchangeResponse.getHeaders());
    }
}