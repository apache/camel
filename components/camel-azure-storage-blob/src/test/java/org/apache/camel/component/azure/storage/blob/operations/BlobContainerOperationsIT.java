package org.apache.camel.component.azure.storage.blob.operations;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.PublicAccessType;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.blob.BlobConfiguration;
import org.apache.camel.component.azure.storage.blob.BlobConstants;
import org.apache.camel.component.azure.storage.blob.BlobTestUtils;
import org.apache.camel.component.azure.storage.blob.client.BlobClientFactory;
import org.apache.camel.component.azure.storage.blob.client.BlobServiceClientWrapper;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BlobContainerOperationsIT extends CamelTestSupport {

    private BlobConfiguration configuration;
    private BlobServiceClientWrapper blobServiceClientWrapper;

    @BeforeAll
    public void setup() throws IOException {
        final Properties properties = BlobTestUtils.loadAzurePropertiesFile();

        configuration = new BlobConfiguration();
        configuration.setAccountName(properties.getProperty("account_name"));
        configuration.setAccessKey(properties.getProperty("access_key"));

        blobServiceClientWrapper = new BlobServiceClientWrapper(BlobClientFactory.createBlobServiceClient(configuration));
    }

    @Test
    public void testCreateAndDeleteContainer() throws InterruptedException {
        final BlobContainerOperations blobContainerOperations = new BlobContainerOperations(configuration, blobServiceClientWrapper.getBlobContainerClientWrapper("testcontainer1"));

        final BlobOperationResponse response = blobContainerOperations.createContainer(null);

        assertNotNull(response);
        assertNotNull(response.getHeaders().get(BlobConstants.HTTP_HEADERS));
        assertTrue((boolean)response.getBody());

        // delete everything
        blobContainerOperations.deleteContainer(null);

        // give a grace period
        TimeUnit.SECONDS.sleep(40);

        // test with options being set
        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(BlobConstants.METADATA, Collections.singletonMap("testKeyMetadata", "testValueMetadata"));
        exchange.getIn().setHeader(BlobConstants.PUBLIC_ACCESS_TYPE, PublicAccessType.CONTAINER);

        final BlobOperationResponse response1 = blobContainerOperations.createContainer(exchange);

        assertNotNull(response1);
        assertNotNull(response1.getHeaders().get(BlobConstants.HTTP_HEADERS));
        assertTrue((boolean)response1.getBody());

        blobContainerOperations.deleteContainer(null);
    }

    @Test
    public void testListBlobs() {
        final BlobContainerOperations blobContainerOperations = new BlobContainerOperations(configuration, blobServiceClientWrapper.getBlobContainerClientWrapper("test"));

        @SuppressWarnings("unchecked")
        final List<String> items = ((List<BlobItem>) blobContainerOperations.listBlobs(null).getBody()).stream().map(BlobItem::getName).collect(Collectors.toList());

        assertNotNull(items);
        assertTrue(items.contains("test_file"));
    }
}
