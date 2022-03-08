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
package org.apache.camel.component.google.storage.unit;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import com.google.cloud.storage.Blob;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.FileConsumer;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.google.storage.GoogleCloudStorageConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProducerStoreFileTest extends GoogleCloudStorageBaseTest {
    private static final String FILE_ENDPOINT = "target/test-classes/tostore";
    private static final String FILE_NAME = "FileToStore.txt";

    @EndpointInject
    private ProducerTemplate template;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                String endpoint = "google-storage://myCamelBucket?autoCreateBucket=true";

                from("direct:fromFile").to(endpoint);
            }
        };
    }

    @Test
    public void testStoreFromFileWithContentLength() throws IOException {
        Path filePath = Path.of(FILE_ENDPOINT, FILE_NAME);
        Exchange storeFileExchange = template.request("direct:fromFile", exchange -> {
            exchange.getIn().setHeader(GoogleCloudStorageConstants.OBJECT_NAME, FILE_NAME);
            exchange.getIn().setHeader(GoogleCloudStorageConstants.CONTENT_ENCODING, "text/plain");
            exchange.getIn().setHeader(GoogleCloudStorageConstants.CONTENT_LENGTH, Files.size(filePath));
            GenericFile<File> genericFile = FileConsumer.asGenericFile(FILE_ENDPOINT,
                    filePath.toFile(), StandardCharsets.UTF_8.name(), false);
            exchange.getIn().setBody(genericFile);
        });
        assertNotNull(storeFileExchange);
        Blob fileBlob = storeFileExchange.getMessage().getBody(Blob.class);
        assertNotNull(fileBlob);
        assertEquals(FILE_NAME, fileBlob.getName());
        assertEquals(Files.size(filePath), Long.valueOf(fileBlob.getMetadata().get("Content-Length")));
        byte[] blobContents = fileBlob.getContent();
        assertNotNull(blobContents);
        assertEquals(Files.readString(filePath), new String(blobContents));
    }

    @Test
    public void testStoreFromFileWithNoContentLength() throws IOException {
        Path filePath = Path.of(FILE_ENDPOINT, FILE_NAME);
        Exchange storeFileExchange = template.request("direct:fromFile", exchange -> {
            exchange.getIn().setHeader(GoogleCloudStorageConstants.OBJECT_NAME, FILE_NAME);
            exchange.getIn().setHeader(GoogleCloudStorageConstants.CONTENT_ENCODING, "text/plain");
            GenericFile<File> genericFile = FileConsumer.asGenericFile(FILE_ENDPOINT,
                    filePath.toFile(), StandardCharsets.UTF_8.name(), false);
            exchange.getIn().setBody(genericFile);
        });
        assertNotNull(storeFileExchange);
        Blob fileBlob = storeFileExchange.getMessage().getBody(Blob.class);
        assertNotNull(fileBlob);
        assertEquals(FILE_NAME, fileBlob.getName());
        assertEquals(Files.size(filePath), Long.valueOf(fileBlob.getMetadata().get("Content-Length")));
        byte[] blobContents = fileBlob.getContent();
        assertNotNull(blobContents);
        assertEquals(Files.readString(filePath), new String(blobContents));
    }

    @Test
    public void testStoreFromstream() {
        final String fileName = "FromStream.txt";
        byte[] payload = "Hi, How are you ?".getBytes();
        ByteArrayInputStream bais = new ByteArrayInputStream(payload);
        Exchange storeFileExchange = template.request("direct:fromFile", exchange -> {
            exchange.getIn().setHeader(GoogleCloudStorageConstants.OBJECT_NAME, fileName);
            exchange.getIn().setHeader(GoogleCloudStorageConstants.CONTENT_ENCODING, "text/plain");
            exchange.getIn().setBody(bais);
        });
        assertNotNull(storeFileExchange);
        Blob fileBlob = storeFileExchange.getMessage().getBody(Blob.class);
        assertNotNull(fileBlob);
        assertEquals(fileName, fileBlob.getName());
        assertEquals(payload.length, Integer.valueOf(fileBlob.getMetadata().get("Content-Length")));
        byte[] blobContents = fileBlob.getContent();
        assertTrue(Arrays.equals(payload, blobContents));
    }
}
