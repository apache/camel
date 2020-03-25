package org.apache.camel.component.azure.storage.blob.operations;

import java.util.List;
import java.util.stream.Collectors;

import com.azure.storage.blob.models.BlobItem;
import org.apache.camel.component.azure.storage.blob.BlobConfiguration;
import org.apache.camel.component.azure.storage.blob.BlobConstants;
import org.apache.camel.component.azure.storage.blob.client.BlobContainerClientAdapterMockImpl;
import org.apache.camel.component.azure.storage.blob.client.adapter.BlobContainerClientAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlobContainerOperationsTest {

    private BlobConfiguration configuration;
    private BlobContainerClientAdapter containerClientAdapter;

    @BeforeEach
    public void setup() {
        configuration = new BlobConfiguration();
        configuration.setAccountName("cameldev");
        configuration.setContainerName("awesome2");

        containerClientAdapter = new BlobContainerClientAdapterMockImpl();
    }

    @Test
    public void testCreateContainer() {
        final BlobContainerOperations blobContainerOperations = new BlobContainerOperations(configuration, containerClientAdapter);
        final BlobOperationResponse response = blobContainerOperations.createContainer();

        assertNotNull(response);
        assertNotNull(response.getHeaders().get(BlobConstants.HTTP_HEADERS));
        assertTrue((boolean)response.getBody());

        // throw an error in case of no container name set
        assertThrows(IllegalArgumentException.class, () -> {
           configuration.setContainerName(null);
           blobContainerOperations.createContainer();
        });
    }

    @Test
    public void testDeleteContainer() {
        final BlobContainerOperations blobContainerOperations = new BlobContainerOperations(configuration, containerClientAdapter);
        final BlobOperationResponse response = blobContainerOperations.deleteContainer();

        assertNotNull(response);
        assertNotNull(response.getHeaders().get(BlobConstants.HTTP_HEADERS));
        assertTrue((boolean)response.getBody());

        // throw an error in case of no container name set
        assertThrows(IllegalArgumentException.class, () -> {
            configuration.setContainerName(null);
            blobContainerOperations.deleteContainer();
        });
    }

    @Test
    public void testListBlob() {
        final BlobContainerOperations blobContainerOperations = new BlobContainerOperations(configuration, containerClientAdapter);
        final BlobOperationResponse response = blobContainerOperations.listBlobs();

        assertNotNull(response);

        @SuppressWarnings("unchecked")
        final List<String> items = ((List<BlobItem>) response.getBody()).stream().map(BlobItem::getName).collect(Collectors.toList());

        assertTrue(items.contains("item-1"));
        assertTrue(items.contains("item-2"));

        // throw an error in case of no container name set
        assertThrows(IllegalArgumentException.class, () -> {
            configuration.setContainerName(null);
            blobContainerOperations.listBlobs();
        });
    }

}