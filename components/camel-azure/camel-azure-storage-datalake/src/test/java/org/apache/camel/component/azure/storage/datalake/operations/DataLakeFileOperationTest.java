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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;

import com.azure.core.http.HttpHeaders;
import com.azure.core.http.rest.ResponseBase;
import com.azure.storage.file.datalake.DataLakeFileClient;
import com.azure.storage.file.datalake.models.DataLakeFileOpenInputStreamResult;
import com.azure.storage.file.datalake.models.DataLakeRequestConditions;
import com.azure.storage.file.datalake.models.FileRange;
import com.azure.storage.file.datalake.models.FileReadAsyncResponse;
import com.azure.storage.file.datalake.models.FileReadHeaders;
import com.azure.storage.file.datalake.models.FileReadResponse;
import com.azure.storage.file.datalake.models.PathInfo;
import com.azure.storage.file.datalake.models.PathProperties;
import com.azure.storage.file.datalake.options.DataLakeFileInputStreamOptions;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.datalake.DataLakeConfiguration;
import org.apache.camel.component.azure.storage.datalake.DataLakeConstants;
import org.apache.camel.component.azure.storage.datalake.client.DataLakeFileClientWrapper;
import org.apache.camel.component.file.FileConsumer;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
public class DataLakeFileOperationTest extends CamelTestSupport {

    private static final String LOCAL_CONTENT = "local file content";
    private static final String REMOTE_CONTENT = "remote file content";

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
    void testGetFileWithFileBodyDoesNotWriteIntoTheFile(@TempDir Path dir) throws IOException {
        // a File body converts to an OutputStream, but only an OutputStream body is written to
        final Path file = Files.writeString(dir.resolve("local.txt"), LOCAL_CONTENT);

        assertGetFileDoesNotWriteIntoTheFile(file.toFile(), file);
    }

    @Test
    void testGetFileWithPathBodyDoesNotWriteIntoTheFile(@TempDir Path dir) throws IOException {
        final Path file = Files.writeString(dir.resolve("local.txt"), LOCAL_CONTENT);

        assertGetFileDoesNotWriteIntoTheFile(file, file);
    }

    @Test
    void testGetFileWithGenericFileBodyDoesNotWriteIntoTheFile(@TempDir Path dir) throws IOException {
        // the body of an exchange of a file consumer
        final Path file = Files.writeString(dir.resolve("local.txt"), LOCAL_CONTENT);

        assertGetFileDoesNotWriteIntoTheFile(FileConsumer.asGenericFile(dir.toString(), file.toFile(), null, false), file);
    }

    private void assertGetFileDoesNotWriteIntoTheFile(Object body, Path file) throws IOException {
        when(client.openInputStream()).thenReturn(new ByteArrayInputStream(REMOTE_CONTENT.getBytes(StandardCharsets.UTF_8)));
        // a stream over the file given to the download would get the remote content
        lenient().when(client.downloadWithResponse(any(), any(), any(), any(), anyBoolean(), any()))
                .thenAnswer(invocation -> download(invocation.getArgument(0)));

        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);

        final DataLakeOperationResponse response = new DataLakeFileOperations(configuration, client).getFile(exchange);

        assertEquals(LOCAL_CONTENT, Files.readString(file));
        assertEquals(REMOTE_CONTENT,
                new String(((InputStream) response.getBody()).readAllBytes(), StandardCharsets.UTF_8));
        verify(client, never()).downloadWithResponse(any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void testGetFileWithOutputStreamBodyWritesIntoTheStream() throws IOException {
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        when(client.downloadWithResponse(same(body), any(), any(), any(), anyBoolean(), any()))
                .thenAnswer(invocation -> download(invocation.getArgument(0)));

        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);

        final DataLakeOperationResponse response = new DataLakeFileOperations(configuration, client).getFile(exchange);

        assertSame(body, response.getBody());
        assertEquals(REMOTE_CONTENT, body.toString(StandardCharsets.UTF_8));
        verify(client, never()).openInputStream();
    }

    @Test
    void testGetFileWithoutBodyReturnsAnInputStream() throws IOException {
        when(client.openInputStream()).thenReturn(new ByteArrayInputStream(REMOTE_CONTENT.getBytes(StandardCharsets.UTF_8)));

        final DataLakeOperationResponse response
                = new DataLakeFileOperations(configuration, client).getFile(new DefaultExchange(context));

        assertEquals(REMOTE_CONTENT,
                new String(((InputStream) response.getBody()).readAllBytes(), StandardCharsets.UTF_8));
        verify(client, never()).downloadWithResponse(any(), any(), any(), any(), anyBoolean(), any());
    }

    private static FileReadResponse download(OutputStream outputStream) throws IOException {
        outputStream.write(REMOTE_CONTENT.getBytes(StandardCharsets.UTF_8));
        return new FileReadResponse(new FileReadAsyncResponse(null, 200, new HttpHeaders(), null, new FileReadHeaders()));
    }

    @Test
    void testDownloadToFileRejectsPathTraversalName() {
        // a file name is influenced by whoever writes to the file system and may contain ../ segments;
        // the download must stay within the configured fileDir
        configuration.setFileDir("/tmp/camel-azure-datalake-download");
        when(client.getFileName()).thenReturn("../escaped/PROOF_PWNED");

        final DataLakeFileOperations operations = new DataLakeFileOperations(configuration, client);
        final Exchange exchange = new DefaultExchange(context);

        assertThrows(IllegalArgumentException.class, () -> operations.downloadToFile(exchange));
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

    @Test
    void testFlushUsesAbsoluteHeaderPosition() {
        when(client.flushWithResponse(anyLong(), any(), any(), any(), any(), any()))
                .thenReturn(new ResponseBase<>(
                        null, 200, new HttpHeaders(),
                        new PathInfo("testTag", OffsetDateTime.now()), null));
        final Exchange exchange = new DefaultExchange(context);
        configuration.setPosition(110L);
        exchange.getIn().setHeader(DataLakeConstants.POSITION, 120L);

        new DataLakeFileOperations(configuration, client).flushToFile(exchange);

        verify(client).flushWithResponse(eq(120L), any(), any(), any(), any(), any());
        verify(client, never()).getFileSize();
    }

    @Test
    void testFlushUsesAbsoluteConfiguredPosition() {
        configuration.setPosition(120L);
        when(client.flushWithResponse(anyLong(), any(), any(), any(), any(), any()))
                .thenReturn(new ResponseBase<>(
                        null, 200, new HttpHeaders(),
                        new PathInfo("testTag", OffsetDateTime.now()), null));

        new DataLakeFileOperations(configuration, client).flushToFile(new DefaultExchange(context));

        verify(client).flushWithResponse(eq(120L), any(), any(), any(), any(), any());
        verify(client, never()).getFileSize();
    }

    @Test
    void testFlushPreservesCurrentLengthFallbackWhenPositionIsAbsent() {
        // Preserve the unchanged null-position fallback introduced by CAMEL-24159.
        when(client.getFileSize()).thenReturn(100L);
        when(client.flushWithResponse(anyLong(), any(), any(), any(), any(), any()))
                .thenReturn(new ResponseBase<>(
                        null, 200, new HttpHeaders(),
                        new PathInfo("testTag", OffsetDateTime.now()), null));

        new DataLakeFileOperations(configuration, client).flushToFile(new DefaultExchange(context));

        verify(client).flushWithResponse(eq(100L), any(), any(), any(), any(), any());
    }

    @Test
    void testGetFileUsesHeaderRangeForInputStream() throws IOException {
        configuration.setFileOffset(2L);
        configuration.setDataCount(3L);
        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(DataLakeConstants.FILE_RANGE, new FileRange(4, 2L));
        assertInputStreamRange(exchange, 4, 2L);
    }

    @Test
    void testGetFileUsesConfiguredRangeForInputStream() throws IOException {
        configuration.setFileOffset(2L);
        configuration.setDataCount(3L);
        assertInputStreamRange(new DefaultExchange(context), 2, 3L);
    }

    private void assertInputStreamRange(Exchange exchange, long offset, long count) throws IOException {
        final DataLakeFileInputStreamOptions options = inputStreamOptions(exchange);
        assertEquals(offset, options.getRange().getOffset());
        assertEquals(count, options.getRange().getCount());
    }

    @Test
    void testGetFileAppliesConditionsWithHeaderRange() throws IOException {
        configuration.setFileOffset(2L);
        configuration.setDataCount(3L);
        final Exchange exchange = new DefaultExchange(context);
        final DataLakeRequestConditions conditions = new DataLakeRequestConditions().setIfMatch("etag");
        exchange.getIn().setHeader(DataLakeConstants.FILE_RANGE, new FileRange(4, 2L));
        exchange.getIn().setHeader(DataLakeConstants.DATALAKE_REQUEST_CONDITION, conditions);

        final DataLakeFileInputStreamOptions options = inputStreamOptions(exchange);

        assertEquals(4, options.getRange().getOffset());
        assertEquals(2L, options.getRange().getCount());
        assertSame(conditions, options.getRequestConditions());
    }

    @Test
    void testGetFileAppliesConditionsWithoutRange() throws IOException {
        final Exchange exchange = new DefaultExchange(context);
        final DataLakeRequestConditions conditions = new DataLakeRequestConditions().setLeaseId("lease");
        exchange.getIn().setHeader(DataLakeConstants.DATALAKE_REQUEST_CONDITION, conditions);

        final DataLakeFileInputStreamOptions options = inputStreamOptions(exchange);

        assertNull(options.getRange());
        assertSame(conditions, options.getRequestConditions());
    }

    private DataLakeFileInputStreamOptions inputStreamOptions(Exchange exchange) throws IOException {
        final InputStream stream = new ByteArrayInputStream("range".getBytes(Charset.defaultCharset()));
        final DataLakeFileOpenInputStreamResult result = mock(DataLakeFileOpenInputStreamResult.class);
        when(result.getInputStream()).thenReturn(stream);
        when(alternateClient.openInputStream(any(DataLakeFileInputStreamOptions.class))).thenReturn(result);
        exchange.getIn().setHeader(DataLakeConstants.FILE_CLIENT, alternateClient);

        final DataLakeOperationResponse response = new DataLakeFileOperations(configuration, client).getFile(exchange);

        assertEquals(stream, response.getBody());
        final ArgumentCaptor<DataLakeFileInputStreamOptions> options
                = ArgumentCaptor.forClass(DataLakeFileInputStreamOptions.class);
        verify(alternateClient).openInputStream(options.capture());
        return options.getValue();
    }

}
