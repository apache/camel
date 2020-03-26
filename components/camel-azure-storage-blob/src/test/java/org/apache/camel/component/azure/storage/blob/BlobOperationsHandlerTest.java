package org.apache.camel.component.azure.storage.blob;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Objects;

import com.azure.storage.blob.BlobServiceClient;

import com.azure.storage.blob.models.BlobContainerItem;
import com.azure.storage.blob.models.BlobItem;
import org.apache.camel.component.azure.storage.blob.client.BlobClientFactory;
import org.apache.camel.component.azure.storage.blob.operations.BlobContainerOperations;
import org.apache.camel.component.azure.storage.blob.operations.BlobOperationResponse;
import org.apache.camel.component.azure.storage.blob.operations.BlobOperations;
import org.apache.camel.component.azure.storage.blob.operations.BlobServiceOperations;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

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

    /*@Test
    public void testHandleListBlobContainers() {
        final BlobServiceClient client = BlobClientFactory.createBlobServiceClient(configuration);
        final BlobServiceOperations blobServiceOperations = new BlobServiceOperations(configuration, client);

        final List<BlobContainerItem> blobContainerItems = (List<BlobContainerItem>) blobServiceOperations.listBlobContainers().getBody();

        blobContainerItems.forEach(blobContainerItem -> System.out.println(blobContainerItem.getName()));
    }*/


    /*@Test
    public void testHandleListBlobs() {
        configuration.setContainerName("test");

        final BlobContainerOperations blobContainerOperations = new BlobContainerOperations(configuration, BlobClientFactory.createBlobContainerClient(configuration));

        final List<BlobItem> blobContainerItems = (List<BlobItem>) blobContainerOperations.listBlobs().getBody();

        blobContainerItems.forEach(blobItem -> System.out.println(blobItem.getName()));
    }*/

    @Test
    public void testHandleGetBlob() throws IOException {
        configuration.setContainerName("test");
        configuration.setBlobName("0b4e673827795_1_V550.jpg");
        configuration.setFileDir("/Users/oalsafi/Work/Apache/camel/components/camel-azure-storage-blob");


        final BlobOperations blobOperations = new BlobOperations(configuration, BlobClientFactory.createBlobClient(configuration));

        //final BlobDownloadResponse downloadResponse = handler.handleDownloadBlob(client);

        //System.out.println(downloadResponse.getDeserializedHeaders());

        final BlobOperationResponse response = blobOperations.downloadBlob();

        System.out.println(response.getHeaders());
    }

    @Test
    public void handleDeleteBlob() {
        configuration.setContainerName("test");
        configuration.setBlobName("Sharklets_Texture.png");

        final BlobOperations blobOperations = new BlobOperations(configuration, BlobClientFactory.createBlobClient(configuration));

        final BlobOperationResponse blobOperationResponse = blobOperations.deleteBlob();

        System.out.println(blobOperationResponse.getHeaders());
    }
}