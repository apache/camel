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
package org.apache.camel.component.minio.integration;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.WrappedFile;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.minio.MinioConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Integration tests for MinioProducer putObject with various body types.
 *
 * Verifies that String, InputStream, byte[], and WrappedFile bodies are all correctly uploaded to Minio with proper
 * content-length handling.
 *
 */
class MinioPutObjectBodyTypesIT extends MinioIntegrationTestSupport {

    private static final String BUCKET = "test-body-types";
    private static final String CONTENT = "Hello from Camel Minio test";

    @TempDir
    File tempDir;

    @BindToRegistry("minioClient")
    MinioClient client = MinioClient.builder()
            .endpoint("http://" + service.host(), service.port(), false)
            .credentials(service.accessKey(), service.secretKey())
            .build();

    @EndpointInject
    private ProducerTemplate template;

    @Test
    void putObjectWithStringBody() throws Exception {
        Exchange result = template.send("direct:putObject", exchange -> {
            exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, "string-body.txt");
            exchange.getIn().setBody(CONTENT);
        });

        assertNull(result.getException(), "Exchange should not have an exception");
        assertEquals(CONTENT, readObject("string-body.txt"));
    }

    @Test
    void putObjectWithInputStreamBody() throws Exception {
        Exchange result = template.send("direct:putObject", exchange -> {
            exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, "stream-body.txt");
            exchange.getIn().setBody(new ByteArrayInputStream(CONTENT.getBytes(StandardCharsets.UTF_8)));
        });

        assertNull(result.getException(), "Exchange should not have an exception");
        assertEquals(CONTENT, readObject("stream-body.txt"));
    }

    @Test
    void putObjectWithByteArrayBody() throws Exception {
        Exchange result = template.send("direct:putObject", exchange -> {
            exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, "bytes-body.txt");
            exchange.getIn().setBody(CONTENT.getBytes(StandardCharsets.UTF_8));
        });

        assertNull(result.getException(), "Exchange should not have an exception");
        assertEquals(CONTENT, readObject("bytes-body.txt"));
    }

    @Test
    void putObjectWithWrappedFileBody() throws Exception {
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

        Exchange result = template.send("direct:putObject", exchange -> {
            exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, "wrapped-file-body.txt");
            exchange.getIn().setBody(wrappedFile);
        });

        assertNull(result.getException(), "Exchange should not have an exception");
        assertEquals(CONTENT, readObject("wrapped-file-body.txt"));
    }

    private String readObject(String objectName) throws Exception {
        try (InputStream is = client.getObject(GetObjectArgs.builder()
                .bucket(BUCKET)
                .object(objectName)
                .build())) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:putObject")
                        .to("minio://" + BUCKET + "?autoCreateBucket=true");
            }
        };
    }
}
