package org.apache.camel.component.azure.storage.blob.operations;

import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.specialized.BlobInputStream;
import org.apache.camel.component.azure.storage.blob.BlobConfiguration;
import org.apache.camel.component.azure.storage.blob.client.BlobClientWrapper;
import org.apache.camel.component.azure.storage.blob.client.BlobContainerClientWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
public class BlobOperationsTest {

    private BlobConfiguration configuration;

    //@Mock
    //private BlobClientWrapper client;

    //@Mock
    //private BlobInputStream blobInputStream;

    @BeforeEach
    public void setup() {
        configuration = new BlobConfiguration();
        configuration.setAccountName("cameldev");
        configuration.setContainerName("awesome2");
    }

    @Test
    @Disabled
    public void testGetBlob() {
        //final BlobProperties blobProperties = new BlobProperties();
        //when(blobInputStream.getProperties()).thenReturn(new BlobProperties())
        //when(client.openInputStream(any(), any())).thenReturn(new BlobInputStream(any(), any()))
    }
}
