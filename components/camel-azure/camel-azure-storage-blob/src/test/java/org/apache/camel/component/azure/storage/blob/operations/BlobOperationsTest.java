/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.azure.storage.blob.operations;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import com.azure.core.http.HttpHeaders;
import com.azure.core.http.rest.ResponseBase;
import com.azure.storage.blob.models.BlobDownloadHeaders;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlockBlobItem;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.blob.BlobBlock;
import org.apache.camel.component.azure.storage.blob.BlobConfiguration;
import org.apache.camel.component.azure.storage.blob.BlobConstants;
import org.apache.camel.component.azure.storage.blob.BlobType;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
class BlobOperationsTest extends CamelTestSupport {

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
    void testGetBlob() throws IOException {
        // mocking
        final Map<String, Object> mockedResults = new HashMap<>();
        mockedResults.put("inputStream", new ByteArrayInputStream("testInput".getBytes(Charset.defaultCharset())));
        mockedResults.put("properties", createBlobProperties());

        when(client.openInputStream(any(), any())).thenReturn(mockedResults);

        final Exchange exchange = new DefaultExchange(context);

        // first: test with no exchange provided
        final BlobOperations operations = new BlobOperations(configuration, client);

        final BlobOperationResponse response = operations.getBlob(null);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertNotNull(response.getHeaders());
        assertNotNull(response.getHeaders().get(BlobConstants.CREATION_TIME));
        assertEquals("testInput", new BufferedReader(new InputStreamReader((InputStream) response.getBody())).readLine());

        // second: test with exchange provided
        configuration.setBlobType(BlobType.blockblob);
        final BlobOperationResponse response2 = operations.getBlob(exchange);

        assertNotNull(response2);
        assertNotNull(response2.getBody());
        assertNotNull(response2.getHeaders());
        assertNotNull(response2.getHeaders().get(BlobConstants.CREATION_TIME));

        // third: test with exchange provided but with outputstream set
        // mocking
        final ResponseBase<BlobDownloadHeaders, Void> mockedResults2 = new ResponseBase<>(
                null, 200, new HttpHeaders().set("x-test-header", "123"), null, new BlobDownloadHeaders().setETag("tag1"));
        when(client.downloadWithResponse(any(), any(), any(), any(), anyBoolean(), any())).thenReturn(mockedResults2);
        exchange.getIn().setBody(new ByteArrayOutputStream());

        final BlobOperationResponse response3 = operations.getBlob(exchange);

        assertNotNull(response3);
        assertNotNull(response3.getBody());
        assertNotNull(response3.getHeaders());
        assertEquals("tag1", response3.getHeaders().get(BlobConstants.E_TAG));
    }

    @Test
    void testUploadBlockBlob() throws Exception {
        // mocking
        final BlockBlobItem blockBlobItem = new BlockBlobItem("testTag", OffsetDateTime.now(), null, false, null);
        final HttpHeaders httpHeaders = new HttpHeaders().set("x-test-header", "123");

        when(client.uploadBlockBlob(any(), anyLong(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ResponseBase<>(null, 200, httpHeaders, blockBlobItem, null));

        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new ByteArrayInputStream("test".getBytes(Charset.defaultCharset())));

        // test upload with input stream
        final BlobOperations operations = new BlobOperations(configuration, client);

        final BlobOperationResponse operationResponse = operations.uploadBlockBlob(exchange);

        assertNotNull(operationResponse);
        assertTrue((boolean) operationResponse.getBody());
        assertNotNull(operationResponse.getHeaders());
        assertEquals("testTag", operationResponse.getHeaders().get(BlobConstants.E_TAG));
        assertEquals("123", ((HttpHeaders) operationResponse.getHeaders().get(BlobConstants.RAW_HTTP_HEADERS))
                .get("x-test-header").getValue());
    }

    @Test
    void testStageBlockBlobList() throws Exception {
        final HttpHeaders httpHeaders = new HttpHeaders().set("x-test-header", "123");
        when(client.stageBlockBlob(anyString(), any(), anyLong(), any(), any(), any())).thenReturn(httpHeaders);

        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("test");
        exchange.getIn().setHeader(BlobConstants.COMMIT_BLOCK_LIST_LATER, true);

        // test
        final BlobOperations operations = new BlobOperations(configuration, client);

        // in case of invalid payload
        assertThrows(IllegalArgumentException.class, () -> operations.stageBlockBlobList(exchange));

        // in case of correct payload
        exchange.getIn().setBody(BlobBlock.createBlobBlock("1", new ByteArrayInputStream("test".getBytes())));

        // test again
        final BlobOperationResponse response = operations.stageBlockBlobList(exchange);

        assertNotNull(response);
        assertTrue((boolean) response.getBody());
    }

    private BlobProperties createBlobProperties() {
        return new BlobProperties(
                OffsetDateTime.now(), null, null, 0L, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null);
    }
}
