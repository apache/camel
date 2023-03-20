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
package org.apache.camel.component.azure.storage.datalake.operations;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.OffsetDateTime;

import com.azure.core.http.HttpHeaders;
import com.azure.core.http.rest.ResponseBase;
import com.azure.storage.file.datalake.DataLakeFileClient;
import com.azure.storage.file.datalake.models.PathInfo;
import com.azure.storage.file.datalake.models.PathProperties;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.datalake.DataLakeConfiguration;
import org.apache.camel.component.azure.storage.datalake.DataLakeConstants;
import org.apache.camel.component.azure.storage.datalake.client.DataLakeFileClientWrapper;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
public class DataLakeFileOperationTest extends CamelTestSupport {

    private DataLakeConfiguration configuration;

    @Mock
    private DataLakeFileClientWrapper client;

    @Mock
    private DataLakeFileClient alternateClient;

    @BeforeEach
    public void setup() {
        configuration = new DataLakeConfiguration();
        configuration.setAccountName("cameltesting");
    }

    @Test
    void testGetFile() throws IOException {
        InputStream mockStream = new ByteArrayInputStream("testInput".getBytes(Charset.defaultCharset()));

        when(client.openInputStream()).thenReturn(mockStream);

        final Exchange exchange = new DefaultExchange(context);
        final DataLakeFileOperations operations = new DataLakeFileOperations(configuration, client);

        final DataLakeOperationResponse response = operations.getFile(null);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertNotNull(response.getHeaders());
        assertEquals("testInput",
                new BufferedReader(new InputStreamReader((InputStream) response.getBody(), Charset.defaultCharset()))
                        .readLine());

        final DataLakeOperationResponse secondResponse = operations.getFile(exchange);

        assertNotNull(secondResponse);
        assertNotNull(secondResponse.getBody());
        assertNotNull(secondResponse.getHeaders());
    }

    @Test
    void testUploadFile() throws Exception {
        final OffsetDateTime time = OffsetDateTime.now();
        final PathInfo pathInfo = new PathInfo("testTag", time);
        final HttpHeaders httpHeaders = new HttpHeaders();
        when(client.uploadWithResponse(any(), any()))
                .thenReturn(new ResponseBase<>(null, 200, httpHeaders, pathInfo, null));

        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new ByteArrayInputStream("testing".getBytes(Charset.defaultCharset())));

        final DataLakeFileOperations operations = new DataLakeFileOperations(configuration, client);
        final DataLakeOperationResponse response = operations.upload(exchange);

        assertNotNull(response);
        assertTrue((boolean) response.getBody());
        assertNotNull(response.getHeaders());
        assertEquals("testTag", response.getHeaders().get(DataLakeConstants.E_TAG));
        assertEquals(time, response.getHeaders().get(DataLakeConstants.LAST_MODIFIED));
    }

    @Test
    void testServiceClientOverride() throws Exception {
        final HttpHeaders httpHeaders = new HttpHeaders();
        final byte[] testing = "testing".getBytes(Charset.defaultCharset());
        when(alternateClient.appendWithResponse(any(), anyLong(), anyLong(), any(), any(), any()))
                .thenReturn(new ResponseBase<>(null, 200, httpHeaders, null, null));
        final PathProperties properties = mock(PathProperties.class);
        when(properties.getFileSize()).thenReturn(Long.valueOf(testing.length));
        when(alternateClient.getProperties()).thenReturn(properties);

        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(DataLakeConstants.FILE_CLIENT, alternateClient);
        exchange.getIn().setBody(new ByteArrayInputStream(testing));

        final DataLakeFileOperations operations = new DataLakeFileOperations(configuration, client);
        final DataLakeOperationResponse response = operations.appendToFile(exchange);

        assertNotNull(response);
        assertTrue((boolean) response.getBody());
        assertNotNull(response.getHeaders());
    }
}
