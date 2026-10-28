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
package org.apache.camel.component.file.azure;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.azure.core.http.rest.Response;
import com.azure.core.http.rest.SimpleResponse;
import com.azure.core.util.Context;
import com.azure.storage.file.share.FileSmbProperties;
import com.azure.storage.file.share.ShareDirectoryClient;
import com.azure.storage.file.share.ShareFileClient;
import com.azure.storage.file.share.StorageFileOutputStream;
import com.azure.storage.file.share.models.ShareFileHttpHeaders;
import com.azure.storage.file.share.models.ShareFileInfo;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The Azure file is created with its size before the content is written, so the size must be the length of the body,
 * not a CamelFileLength header left from the file the route consumed.
 */
class FilesOperationsStoreLengthTest extends CamelTestSupport {

    private final AtomicLong createdSize = new AtomicLong(-1);
    private final ByteArrayOutputStream written = new ByteArrayOutputStream();
    private FilesOperations operations;

    @BeforeEach
    void setupOperations() throws Exception {
        ShareDirectoryClient dir = mock(ShareDirectoryClient.class);
        ShareFileClient file = mock(ShareFileClient.class);
        StorageFileOutputStream out = mock(StorageFileOutputStream.class);

        Response<Boolean> deleted = new SimpleResponse<>(null, 404, null, false);
        when(dir.deleteFileIfExistsWithResponse(anyString(), nullable(Duration.class), any(Context.class)))
                .thenReturn(deleted);
        when(dir.getFileClient(anyString())).thenReturn(file);
        when(file.createWithResponse(anyLong(), nullable(ShareFileHttpHeaders.class), nullable(FileSmbProperties.class),
                nullable(String.class), nullable(Map.class), nullable(Duration.class), any(Context.class)))
                .thenAnswer(inv -> {
                    createdSize.set(inv.getArgument(0));
                    Response<ShareFileInfo> created = new SimpleResponse<>(null, 201, null, null);
                    return created;
                });
        when(file.getFileOutputStream()).thenReturn(out);
        doAnswer(inv -> {
            written.write((byte[]) inv.getArgument(0), inv.getArgument(1), inv.getArgument(2));
            return null;
        }).when(out).write(any(byte[].class), anyInt(), anyInt());

        FilesEndpoint endpoint = context.getEndpoint(
                "azure-files://account.file.core.windows.net/share?credentialType=AZURE_IDENTITY", FilesEndpoint.class);
        operations = new FilesOperations(endpoint);
        operations.restore(new ArrayDeque<>(List.of(dir)));
    }

    private Exchange exchange(Object body, long fileLengthHeader) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);
        exchange.getIn().setHeader(Exchange.FILE_LENGTH, fileLengthHeader);
        return exchange;
    }

    @Test
    void bodyShorterThanTheFileLengthHeader() {
        // e.g. a file was consumed, and the route split or transformed it before storing the result
        assertTrue(operations.storeFile("out.txt", exchange("abc".getBytes(StandardCharsets.UTF_8), 10), -1));

        assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), written.toByteArray());
        assertEquals(3, createdSize.get(), "the file must not be longer than the body (zero bytes at its end)");
    }

    @Test
    void stringBodyLongerThanTheFileLengthHeader() {
        assertTrue(operations.storeFile("out.txt", exchange("abcdefghijklmnopqrst", 10), -1));

        assertEquals(20, written.size());
        assertEquals(20, createdSize.get(), "the file must be created with the size of the body");
    }

    @Test
    void streamOfUnknownLengthUsesTheFileLengthHeader() {
        // guard: the documented streaming case (a stream whose length is only given by the header) is kept
        InputStream stream = new BufferedInputStream(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));
        assertTrue(operations.storeFile("out.txt", exchange(stream, 5), -1));

        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), written.toByteArray());
        assertEquals(5, createdSize.get());
    }
}
