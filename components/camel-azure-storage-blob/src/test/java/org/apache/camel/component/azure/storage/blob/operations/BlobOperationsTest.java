package org.apache.camel.component.azure.storage.blob.operations;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import com.azure.core.http.HttpHeaders;
import com.azure.storage.blob.models.BlobDownloadHeaders;
import com.azure.storage.blob.models.BlobProperties;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.blob.BlobConfiguration;
import org.apache.camel.component.azure.storage.blob.BlobConstants;
import org.apache.camel.component.azure.storage.blob.client.BlobClientWrapper;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
public class BlobOperationsTest extends CamelTestSupport {

    private BlobConfiguration configuration;

    @Mock
    private BlobClientWrapper client;

    @BeforeEach
    public void setup() {
        configuration = new BlobConfiguration();
        configuration.setAccountName("cameldev");
        configuration.setContainerName("awesome2");
    }

    @Test
    public void testGetBlob() throws IOException {
        final Map<String, Object> mockedResults = new HashMap<>();
        mockedResults.put("inputStream", new ByteArrayInputStream("test".getBytes(Charset.defaultCharset())));
        mockedResults.put("properties", createBlobProperties());
        when(client.openInputStream(any(), any())).thenReturn(mockedResults);


        // first: test with no exchange provided
        final BlobOperations operations = new BlobOperations(configuration, client);

        final BlobOperationResponse response = operations.getBlob(null);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertNotNull(response.getHeaders());
        assertNotNull(response.getHeaders().get(BlobConstants.CREATION_TIME));
        assertEquals("test", new BufferedReader(new InputStreamReader((InputStream) response.getBody())).readLine());

        // second: test with exchange provided
        final Exchange exchange = new DefaultExchange(context);
        final BlobOperationResponse response2 = operations.getBlob(exchange);

        assertNotNull(response2);
        assertNotNull(response2.getBody());
        assertNotNull(response2.getHeaders());
        assertNotNull(response2.getHeaders().get(BlobConstants.CREATION_TIME));
    }

    private BlobProperties createBlobProperties() {
        return new BlobProperties(OffsetDateTime.now(), null, null, 0L, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null);
    }
}
