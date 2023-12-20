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
package org.apache.camel.component.azure.storage.blob.integration;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.models.PageRange;
import com.azure.storage.blob.models.PageRangeItem;
import com.azure.storage.blob.specialized.BlobInputStream;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.blob.BlobBlock;
import org.apache.camel.component.azure.storage.blob.BlobConstants;
import org.apache.camel.component.azure.storage.blob.BlobUtils;
import org.apache.camel.component.azure.storage.blob.client.BlobClientWrapper;
import org.apache.camel.component.azure.storage.blob.client.BlobContainerClientWrapper;
import org.apache.camel.component.azure.storage.blob.client.BlobServiceClientWrapper;
import org.apache.camel.component.azure.storage.blob.operations.BlobOperationResponse;
import org.apache.camel.component.azure.storage.blob.operations.BlobOperations;
import org.apache.camel.converter.stream.FileInputStreamCache;
import org.apache.camel.support.DefaultExchange;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlobOperationsIT extends Base {

    private BlobContainerClientWrapper blobContainerClientWrapper;

    private String randomBlobName;

    @BeforeAll
    public void setup() throws Exception {
        randomBlobName = RandomStringUtils.randomAlphabetic(10);

        blobContainerClientWrapper = new BlobServiceClientWrapper(serviceClient)
                .getBlobContainerClientWrapper(configuration.getContainerName());

        // create test container
        blobContainerClientWrapper.createContainer(null, null, null);

        // create test blob
        final InputStream inputStream = new ByteArrayInputStream("awesome camel!".getBytes());
        blobContainerClientWrapper.getBlobClientWrapper(randomBlobName).uploadBlockBlob(inputStream,
                BlobUtils.getInputStreamLength(inputStream), null, null, null, null,
                null, null);
    }

    @AfterAll
    public void deleteClient() {
        blobContainerClientWrapper.deleteContainer(null, null);
    }

    @Test
    void testGetBlob(@TempDir Path testDir) throws IOException {
        final BlobClientWrapper blobClientWrapper = blobContainerClientWrapper.getBlobClientWrapper(randomBlobName);
        final BlobOperations operations = new BlobOperations(configuration, blobClientWrapper);

        // first: test with exchange set but no outputstream set
        final Exchange exchange = new DefaultExchange(context);
        final BlobOperationResponse response1 = operations.getBlob(exchange);

        assertNotNull(response1);
        assertNotNull(response1.getBody());
        assertNotNull(response1.getHeaders());

        final BlobInputStream inputStream = (BlobInputStream) response1.getBody();
        final String bufferedText = new BufferedReader(new InputStreamReader(inputStream)).readLine();

        assertEquals("awesome camel!", bufferedText);

        // second: test with outputstream set on exchange
        final File fileToWrite = new File(testDir.toFile(), "write_test_file.txt");
        exchange.getIn().setBody(new FileOutputStream(fileToWrite));

        final BlobOperationResponse response2 = operations.getBlob(exchange);
        final String fileContent = FileUtils.readFileToString(fileToWrite, Charset.defaultCharset());

        assertNotNull(response2);
        assertNotNull(response2.getBody());
        assertNotNull(response2.getHeaders());
        assertTrue(fileContent.contains("awesome camel!"));
    }

    @Test
    void testDownloadToFile(@TempDir Path testDir) throws IOException {
        final BlobClientWrapper blobClientWrapper = blobContainerClientWrapper.getBlobClientWrapper(randomBlobName);
        final BlobOperations operations = new BlobOperations(configuration, blobClientWrapper);

        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(BlobConstants.FILE_DIR, testDir.toString());
        exchange.getIn().setHeader(BlobConstants.BLOB_NAME, randomBlobName);

        final BlobOperationResponse response = operations.downloadBlobToFile(exchange);

        // third: test with outputstream set on exchange
        final File fileToWrite = testDir.resolve(randomBlobName).toFile();
        final String fileContent = FileUtils.readFileToString(fileToWrite, Charset.defaultCharset());

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertNotNull(response.getHeaders());
        assertNotNull(response.getHeaders().get(BlobConstants.FILE_NAME));
        assertTrue(fileContent.contains("awesome camel!"));
    }

    @Test
    void testDownloadLink() {
        final BlobClientWrapper blobClientWrapper = blobContainerClientWrapper.getBlobClientWrapper(randomBlobName);
        final BlobOperations operations = new BlobOperations(configuration, blobClientWrapper);

        final BlobOperationResponse response = operations.downloadLink(null);

        assertNotNull(response);
        assertTrue((boolean) response.getBody());
        assertNotNull(response.getHeaders().get(BlobConstants.DOWNLOAD_LINK));
    }

    @Test
    void testUploadBlockBlob() throws Exception {
        final BlobClientWrapper blobClientWrapper = blobContainerClientWrapper.getBlobClientWrapper("upload_test_file.txt");
        final BlobOperations operations = new BlobOperations(configuration, blobClientWrapper);

        // first: test as file provided
        final File fileToUpload
                = new File(Objects.requireNonNull(getClass().getClassLoader().getResource("upload_test_file.txt")).getFile());
        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(fileToUpload);

        final BlobOperationResponse response = operations.uploadBlockBlob(exchange);

        assertNotNull(response);
        assertTrue((boolean) response.getBody());
        // check for eTag and md5 to make sure is uploaded
        assertNotNull(response.getHeaders().get(BlobConstants.E_TAG));
        assertNotNull(response.getHeaders().get(BlobConstants.CONTENT_MD5));

        // check content
        final BlobOperationResponse getBlobResponse = operations.getBlob(null);

        assertEquals("awesome camel to upload!",
                IOUtils.toString((InputStream) getBlobResponse.getBody(), Charset.defaultCharset()));

        blobClientWrapper.delete(null, null, null);

        // second: test as string provided
        final String data = "Hello world from my awesome tests!";
        final InputStream dataStream = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        exchange.getIn().setBody(dataStream);

        final BlobOperationResponse response2 = operations.uploadBlockBlob(exchange);

        assertNotNull(response2);
        assertTrue((boolean) response2.getBody());
        // check for eTag and md5 to make sure is uploaded
        assertNotNull(response2.getHeaders().get(BlobConstants.E_TAG));
        assertNotNull(response2.getHeaders().get(BlobConstants.CONTENT_MD5));

        // check content
        final BlobOperationResponse getBlobResponse2 = operations.getBlob(null);

        assertEquals(data, IOUtils.toString((InputStream) getBlobResponse2.getBody(), Charset.defaultCharset()));

        blobClientWrapper.delete(null, null, null);
    }

    @Test
    void testUploadBlockBlobAsCachedStream() throws Exception {
        final BlobClientWrapper blobClientWrapper = blobContainerClientWrapper.getBlobClientWrapper("upload_test_file.txt");
        final BlobOperations operations = new BlobOperations(configuration, blobClientWrapper);

        final File fileToUpload
                = new File(Objects.requireNonNull(getClass().getClassLoader().getResource("upload_test_file.txt")).getFile());
        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new FileInputStreamCache(fileToUpload));

        final BlobOperationResponse response = operations.uploadBlockBlob(exchange);

        assertNotNull(response);
        assertTrue((boolean) response.getBody());
        // check for eTag and md5 to make sure is uploaded
        assertNotNull(response.getHeaders().get(BlobConstants.E_TAG));
        assertNotNull(response.getHeaders().get(BlobConstants.CONTENT_MD5));

        // check content
        final BlobOperationResponse getBlobResponse = operations.getBlob(null);

        assertEquals("awesome camel to upload!",
                IOUtils.toString((InputStream) getBlobResponse.getBody(), Charset.defaultCharset()));

        blobClientWrapper.delete(null, null, null);
    }

    @Test
    void testUploadBlockBlobAsStreamWithBlobSizeHeader() throws Exception {
        final BlobClientWrapper blobClientWrapper = blobContainerClientWrapper.getBlobClientWrapper("upload_test_file.txt");
        final BlobOperations operations = new BlobOperations(configuration, blobClientWrapper);

        final File fileToUpload
                = new File(Objects.requireNonNull(getClass().getClassLoader().getResource("upload_test_file.txt")).getFile());
        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new FileInputStream(fileToUpload));
        exchange.getIn().setHeader(BlobConstants.BLOB_UPLOAD_SIZE, fileToUpload.length());

        final BlobOperationResponse response = operations.uploadBlockBlob(exchange);

        assertNotNull(response);
        assertTrue((boolean) response.getBody());
        // check for eTag and md5 to make sure is uploaded
        assertNotNull(response.getHeaders().get(BlobConstants.E_TAG));
        assertNotNull(response.getHeaders().get(BlobConstants.CONTENT_MD5));

        // check that the size header got removed
        assertNull(exchange.getIn().getHeader(BlobConstants.BLOB_UPLOAD_SIZE));

        // check content
        final BlobOperationResponse getBlobResponse = operations.getBlob(null);

        assertEquals("awesome camel to upload!",
                IOUtils.toString((InputStream) getBlobResponse.getBody(), Charset.defaultCharset()));

        blobClientWrapper.delete(null, null, null);
    }

    @Test
    void testCommitAndStageBlockBlob() throws Exception {
        final BlobClientWrapper blobClientWrapper = blobContainerClientWrapper.getBlobClientWrapper("upload_test_file.txt");
        final BlobOperations operations = new BlobOperations(configuration, blobClientWrapper);

        final List<BlobBlock> blocks = new LinkedList<>();
        blocks.add(BlobBlock.createBlobBlock(new ByteArrayInputStream("Hello".getBytes())));
        blocks.add(BlobBlock.createBlobBlock(new ByteArrayInputStream("From".getBytes())));
        blocks.add(BlobBlock.createBlobBlock(new ByteArrayInputStream("Camel".getBytes())));

        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(blocks);
        exchange.getIn().setHeader(BlobConstants.COMMIT_BLOCK_LIST_LATER, false);

        final BlobOperationResponse response = operations.stageBlockBlobList(exchange);

        assertNotNull(response);
        assertNotNull(response.getBody());
        // check for eTag and md5 to make sure is uploaded
        assertNotNull(response.getHeaders().get(BlobConstants.E_TAG));

        final BlobOperationResponse getBlobResponse = operations.getBlob(null);

        assertEquals("HelloFromCamel", IOUtils.toString((InputStream) getBlobResponse.getBody(), Charset.defaultCharset()));

        blobClientWrapper.delete(null, null, null);
    }

    @Test
    void testGetBlobBlockList() {
        final BlobClientWrapper blobClientWrapper = blobContainerClientWrapper.getBlobClientWrapper(randomBlobName);
        final BlobOperations operations = new BlobOperations(configuration, blobClientWrapper);

        final BlobOperationResponse response = operations.getBlobBlockList(null);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertNotNull(response.getHeaders());
    }

    @Test
    void testCreateAndUpdateAppendBlob() throws IOException {
        final BlobClientWrapper blobClientWrapper = blobContainerClientWrapper.getBlobClientWrapper("upload_test_file.txt");
        final BlobOperations operations = new BlobOperations(configuration, blobClientWrapper);

        final String data = "Hello world from my awesome tests!";
        final InputStream dataStream = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));

        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(dataStream);
        exchange.getIn().setHeader(BlobConstants.CREATE_APPEND_BLOB, true);

        final BlobOperationResponse response = operations.commitAppendBlob(exchange);

        assertNotNull(response);
        assertTrue((boolean) response.getBody());
        // check for eTag and md5 to make sure is uploaded
        assertNotNull(response.getHeaders().get(BlobConstants.E_TAG));
        assertNotNull(response.getHeaders().get(BlobConstants.COMMITTED_BLOCK_COUNT));

        // check content
        final BlobOperationResponse getBlobResponse = operations.getBlob(null);

        assertEquals(data, IOUtils.toString((InputStream) getBlobResponse.getBody(), Charset.defaultCharset()));

        blobClientWrapper.delete(null, null, null);
    }

    @Test
    void testCreateAndUploadPageBlob() throws IOException {
        final BlobClientWrapper blobClientWrapper = blobContainerClientWrapper.getBlobClientWrapper("upload_test_file.txt");
        final BlobOperations operations = new BlobOperations(configuration, blobClientWrapper);

        byte[] dataBytes = new byte[512]; // we set range for the page from 0-511
        new SecureRandom().nextBytes(dataBytes);
        final String data = new String(dataBytes, StandardCharsets.UTF_8);
        final InputStream dataStream = new ByteArrayInputStream(dataBytes);

        final PageRange pageRange = new PageRange().setStart(0).setEnd(511);
        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(dataStream);
        exchange.getIn().setHeader(BlobConstants.PAGE_BLOB_RANGE, pageRange);
        exchange.getIn().setHeader(BlobConstants.CREATE_PAGE_BLOB, true);

        final BlobOperationResponse response = operations.uploadPageBlob(exchange);

        assertNotNull(response);
        assertTrue((boolean) response.getBody());
        assertNotNull(response.getHeaders().get(BlobConstants.E_TAG));

        // check content
        final BlobOperationResponse getBlobResponse = operations.getBlob(null);
        final String dataResponse = IOUtils.toString((InputStream) getBlobResponse.getBody(), StandardCharsets.UTF_8);

        assertEquals(data, dataResponse);

        blobClientWrapper.delete(null, null, null);
    }

    @Test
    void testResizePageBlob() throws IOException {
        final BlobClientWrapper blobClientWrapper = blobContainerClientWrapper.getBlobClientWrapper("upload_test_file.txt");
        final BlobOperations operations = new BlobOperations(configuration, blobClientWrapper);

        byte[] dataBytes = new byte[1024]; // we set range for the page from 0-511
        new SecureRandom().nextBytes(dataBytes);
        final InputStream dataStream = new ByteArrayInputStream(dataBytes);

        final PageRange pageRange = new PageRange().setStart(0).setEnd(1023);
        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(dataStream);
        exchange.getIn().setHeader(BlobConstants.PAGE_BLOB_RANGE, pageRange);
        exchange.getIn().setHeader(BlobConstants.CREATE_PAGE_BLOB, true);

        // create our page
        operations.uploadPageBlob(exchange);

        // create the new size
        exchange.getIn().removeHeader(BlobConstants.PAGE_BLOB_RANGE);
        exchange.getIn().setHeader(BlobConstants.PAGE_BLOB_SIZE, 512L);

        final BlobOperationResponse response = operations.resizePageBlob(exchange);

        assertNotNull(response);
        assertTrue((boolean) response.getBody());

        // check for content
        final BlobOperationResponse getBlobResponse = operations.getBlob(null);
        final BlobInputStream inputStream = (BlobInputStream) getBlobResponse.getBody();
        assertEquals(512, IOUtils.toByteArray(inputStream).length);

        blobClientWrapper.delete(null, null, null);
    }

    @Test
    void testClearPages() throws IOException {
        final BlobClientWrapper blobClientWrapper = blobContainerClientWrapper.getBlobClientWrapper("upload_test_file.txt");
        final BlobOperations operations = new BlobOperations(configuration, blobClientWrapper);

        byte[] dataBytes = new byte[512]; // we set range for the page from 0-511
        new SecureRandom().nextBytes(dataBytes);
        final InputStream dataStream = new ByteArrayInputStream(dataBytes);

        final PageRange pageRange = new PageRange().setStart(0).setEnd(511);
        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(dataStream);
        exchange.getIn().setHeader(BlobConstants.PAGE_BLOB_RANGE, pageRange);
        exchange.getIn().setHeader(BlobConstants.CREATE_PAGE_BLOB, true);

        // create our page
        operations.uploadPageBlob(exchange);

        final BlobOperationResponse response = operations.clearPageBlob(exchange);

        // check content
        final BlobOperationResponse getBlobResponse = operations.getBlob(null);

        // The string returned here is a 512 long sequence of null code points (U+000) which is considered a space for
        // trim(), but not for isBlank.
        assertTrue(IOUtils.toString((InputStream) getBlobResponse.getBody(), StandardCharsets.UTF_8).trim().isEmpty());

        blobClientWrapper.delete(null, null, null);
    }

    @Test
    void testGetPageBlobRanges() throws IOException {
        final BlobClientWrapper blobClientWrapper = blobContainerClientWrapper.getBlobClientWrapper("upload_test_file.txt");
        final BlobOperations operations = new BlobOperations(configuration, blobClientWrapper);

        byte[] dataBytes = new byte[512]; // we set range for the page from 0-511
        new SecureRandom().nextBytes(dataBytes);
        final InputStream dataStream = new ByteArrayInputStream(dataBytes);

        final PageRange pageRange = new PageRange().setStart(0).setEnd(511);
        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(dataStream);
        exchange.getIn().setHeader(BlobConstants.PAGE_BLOB_RANGE, pageRange);
        exchange.getIn().setHeader(BlobConstants.CREATE_PAGE_BLOB, true);

        // create our page
        operations.uploadPageBlob(exchange);

        final BlobOperationResponse response = operations.getPageBlobRanges(exchange);

        assertNotNull(response);

        final PagedIterable<?> pagedIterable = (PagedIterable<?>) response.getBody();
        List<?> pageRangeItems = pagedIterable.stream().toList();

        assertEquals(1, pageRangeItems.size());
        assertInstanceOf(PageRangeItem.class, pageRangeItems.get(0));

        PageRangeItem pageRangeItem = (PageRangeItem) pageRangeItems.get(0);

        assertEquals(pageRange.getStart(), pageRangeItem.getRange().getOffset());
        assertEquals(pageRange.getEnd(), pageRangeItem.getRange().getLength() - 1);
        assertFalse(pageRangeItem.isClear());

        blobClientWrapper.delete(null, null, null);
    }
}
