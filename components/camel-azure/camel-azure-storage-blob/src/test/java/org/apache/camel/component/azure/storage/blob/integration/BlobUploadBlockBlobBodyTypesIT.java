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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.azure.storage.blob.BlobContainerClient;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.WrappedFile;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.storage.blob.BlobConstants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Integration tests for uploadBlockBlob with various body types.
 *
 * Verifies that String, InputStream, byte[], and WrappedFile bodies are all correctly uploaded to Azure Blob Storage
 * with proper content-length handling.
 */
class BlobUploadBlockBlobBodyTypesIT extends Base {

    private static final String CONTENT = "Hello from Camel Azure Storage Blob test";

    @TempDir
    File tempDir;

    @EndpointInject
    private ProducerTemplate template;

    private BlobContainerClient containerClient;

    @BeforeAll
    public void prepare() {
        containerClient = serviceClient.getBlobContainerClient(containerName);
        containerClient.create();
    }

    @Test
    void uploadBlockBlobWithStringBody() throws Exception {
        Exchange result = template.send("direct:uploadBlockBlob", exchange -> {
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, "string-body.txt");
            exchange.getIn().setBody(CONTENT);
        });

        assertNull(result.getException(), "Exchange should not have an exception");
        assertEquals(CONTENT, downloadBlob("string-body.txt"));
    }

    @Test
    void uploadBlockBlobWithInputStreamBody() throws Exception {
        Exchange result = template.send("direct:uploadBlockBlob", exchange -> {
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, "stream-body.txt");
            exchange.getIn().setBody(new ByteArrayInputStream(CONTENT.getBytes(StandardCharsets.UTF_8)));
        });

        assertNull(result.getException(), "Exchange should not have an exception");
        assertEquals(CONTENT, downloadBlob("stream-body.txt"));
    }

    @Test
    void uploadBlockBlobWithByteArrayBody() throws Exception {
        Exchange result = template.send("direct:uploadBlockBlob", exchange -> {
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, "bytes-body.txt");
            exchange.getIn().setBody(CONTENT.getBytes(StandardCharsets.UTF_8));
        });

        assertNull(result.getException(), "Exchange should not have an exception");
        assertEquals(CONTENT, downloadBlob("bytes-body.txt"));
    }

    @Test
    void uploadBlockBlobWithWrappedFileBody() throws Exception {
        File tempFile = new File(tempDir, "wrapped-test.txt");
        Files.writeString(tempFile.toPath(), CONTENT);

        WrappedFile<File> wrappedFile = new WrappedFile<>() {
            @Override
            public File getFile() {
                return tempFile;
            }

            @Override
            public Object getBody() {
                return tempFile;
            }

            @Override
            public long getFileLength() {
                return tempFile.length();
            }
        };

        Exchange result = template.send("direct:uploadBlockBlob", exchange -> {
            exchange.getIn().setHeader(BlobConstants.BLOB_NAME, "wrapped-file-body.txt");
            exchange.getIn().setBody(wrappedFile);
        });

        assertNull(result.getException(), "Exchange should not have an exception");
        assertEquals(CONTENT, downloadBlob("wrapped-file-body.txt"));
    }

    private String downloadBlob(String blobName) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        containerClient.getBlobClient(blobName).downloadStream(outputStream);
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    @AfterAll
    public void deleteContainer() {
        containerClient.delete();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:uploadBlockBlob")
                        .to(String.format("azure-storage-blob://cameldev/%s?operation=uploadBlockBlob", containerName));
            }
        };
    }
}
