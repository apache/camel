package org.apache.camel.component.azure.storage.blob;

import java.io.IOException;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobDownloadResponse;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.blob.client.BlobClientFactory;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
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
        configuration.setAccessKey("2PS1ZnTqmR7ORJ33VSY+ASeq2yuRx+vIx10/+kaT/pu0yYe0iFkZDSMnXpHc33onLg8fxOyN/zx+4hPriZNC+g==");
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
    public void testHandleGetBlob() throws IOException {
        configuration.setContainerName("test");
        configuration.setBlobName("0b4e673827795_1_V550.jpg");
        configuration.setFileDir("/Users/oalsafi/Work/Apache/camel/components/camel-azure-storage-blob");

        final BlobClient client = BlobClientFactory.createBlobClient(configuration);
        final BlobOperationsHandler handler = new BlobOperationsHandler(configuration);

        //final BlobDownloadResponse downloadResponse = handler.handleDownloadBlob(client);

        //System.out.println(downloadResponse.getDeserializedHeaders());

        final Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        handler.handleDownloadBlob(exchange, client);

        System.out.println(exchange.getIn().getBody());
    }
}