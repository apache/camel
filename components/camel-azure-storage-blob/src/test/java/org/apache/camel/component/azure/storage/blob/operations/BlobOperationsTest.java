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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.azure.core.http.HttpHeaders;
import com.azure.storage.blob.models.AccessTier;
import com.azure.storage.blob.models.BlobDownloadHeaders;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlockBlobItem;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.azure.storage.blob.BlobConfiguration;
import org.apache.camel.component.azure.storage.blob.BlobConstants;
import org.apache.camel.component.azure.storage.blob.BlobExchangeHeaders;
import org.apache.camel.component.azure.storage.blob.BlobType;
import org.apache.camel.component.azure.storage.blob.client.BlobClientWrapper;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultMessage;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
public class BlobOperationsTest {

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
        // mocking
        final BlobClientWrapper.ResponseEnvelope<InputStream, BlobProperties> mockedResults = new BlobClientWrapper.ResponseEnvelope<>(new ByteArrayInputStream("testInput".getBytes(Charset.defaultCharset())),
                createBlobProperties());

        when(client.openInputStream(any(), any())).thenReturn(mockedResults);

        final Exchange exchange = mock(Exchange.class);
        final Message message = mock(Message.class);
        when(exchange.getIn()).thenReturn(message);
        when(BlobExchangeHeaders.getTimeoutFromHeaders(exchange)).thenReturn(null);
        when(BlobExchangeHeaders.getBlobRequestConditionsFromHeaders(exchange)).thenReturn(null);

        // first: test with no exchange provided
        final BlobOperations operations = new BlobOperations(configuration, client);

        final BlobOperationResponse response = operations.getBlob(null);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertNotNull(response.getHeaders());
        assertNotNull(response.getHeaders().get(BlobConstants.CREATION_TIME));
        assertEquals("testInput", new BufferedReader(new InputStreamReader((InputStream) response.getBody())).readLine());

        // second: test with exchange provided
        when(exchange.getIn().getBody(OutputStream.class)).thenReturn(null);

        configuration.setBlobType(BlobType.blockblob);
        final BlobOperationResponse response2 = operations.getBlob(exchange);

        assertNotNull(response2);
        assertNotNull(response2.getBody());
        assertNotNull(response2.getHeaders());
        assertNotNull(response2.getHeaders().get(BlobConstants.CREATION_TIME));

        //third: test with exchange provided but with outputstream set
        // mocking
        final BlobClientWrapper.ResponseEnvelope<BlobDownloadHeaders, HttpHeaders> mockedResults2 = new BlobClientWrapper.ResponseEnvelope<>(new BlobDownloadHeaders().setETag("tag1"), new HttpHeaders().put("x-test-header", "123"));
        when(client.downloadWithResponse(any(), any(), any(), any(), anyBoolean(), any())).thenReturn(mockedResults2);
        when(exchange.getIn().getBody(OutputStream.class)).thenReturn(new ByteArrayOutputStream());

        final BlobOperationResponse response3 = operations.getBlob(exchange);

        assertNotNull(response3);
        assertNotNull(response3.getBody());
        assertNotNull(response3.getHeaders());
        assertEquals(response3.getHeaders().get(BlobConstants.E_TAG), "tag1");
    }

    @Test
    public void testUploadBlockBlob() throws Exception {
        // mocking
        final BlockBlobItem blockBlobItem = new BlockBlobItem("testTag", OffsetDateTime.now(), null, false, null);
        final HttpHeaders httpHeaders = new HttpHeaders().put("x-test-header", "123");
        final BlobClientWrapper.ResponseEnvelope<BlockBlobItem, HttpHeaders> mockedResponseEnvelope = new BlobClientWrapper.ResponseEnvelope<>(blockBlobItem, httpHeaders);

        when(client.uploadBlockBlob(any(), anyLong(), any(), anyMap(), any(), any(), any(), any())).thenReturn(mockedResponseEnvelope);

        final Exchange exchange = mock(Exchange.class);
        final Message message = mock(Message.class);
        when(exchange.getIn()).thenReturn(message);
        when(exchange.getIn().getBody()).thenReturn(new ByteArrayInputStream("test".getBytes(Charset.defaultCharset())));
        when(BlobExchangeHeaders.getBlobHttpHeadersFromHeaders(exchange)).thenReturn(null);
        when(BlobExchangeHeaders.getMetadataFromHeaders(exchange)).thenReturn(Collections.emptyMap());
        when(BlobExchangeHeaders.getAccessTierFromHeaders(exchange)).thenReturn(AccessTier.HOT);
        when(BlobExchangeHeaders.getContentMd5FromHeaders(exchange)).thenReturn(null);
        when(BlobExchangeHeaders.getBlobRequestConditionsFromHeaders(exchange)).thenReturn(null);
        when(BlobExchangeHeaders.getTimeoutFromHeaders(exchange)).thenReturn(null);

        // test upload with input stream
        final BlobOperations operations = new BlobOperations(configuration, client);

        final BlobOperationResponse operationResponse = operations.uploadBlockBlob(exchange);

        assertNotNull(operationResponse);
        assertTrue((boolean) operationResponse.getBody());
        assertNotNull(operationResponse.getHeaders());
        assertEquals("testTag", operationResponse.getHeaders().get(BlobConstants.E_TAG));
        assertEquals("123", ((HttpHeaders)operationResponse.getHeaders().get(BlobConstants.RAW_HTTP_HEADERS)).get("x-test-header").getValue());
    }

    private BlobProperties createBlobProperties() {
        return new BlobProperties(OffsetDateTime.now(), null, null, 0L, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null);
    }
}
