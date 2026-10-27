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
package org.apache.camel.component.minio;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The minio consumer sets CamelMinioContentLength to the size of the object it read. When a route changes the body and
 * stores it with a minio producer, that header is stale: the client sends exactly the given number of bytes (minio
 * PartReader), so a longer body was cut and a shorter one failed. The real MinioClient runs against an in-process fake
 * S3 server that stores what it receives.
 */
class MinioProducerStaleContentLengthTest extends CamelTestSupport {

    @TempDir
    Path dir;

    private FakeS3Server s3;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        s3 = new FakeS3Server("bkt");
        s3.start();
        return super.createCamelContext();
    }

    @AfterEach
    void stopServer() {
        if (s3 != null) {
            s3.stop();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:put")
                        .to("minio://bkt?accessKey=xxx&secretKey=yyy&region=us-east-1&endpoint=" + s3.endpoint());
            }
        };
    }

    @Test
    void bodyLongerThanAStaleContentLengthHeaderIsStoredWhole() {
        String text = "a body made longer by the route";

        Exchange result = put("changed.txt", 10L, text);

        assertNull(result.getException());
        assertEquals(text, stored("changed.txt"), "the whole body must be stored, not the length of the header");
    }

    @Test
    void bodyShorterThanAStaleContentLengthHeaderIsStoredWithItsLength() {
        Exchange result = put("short.txt", 1000L, "short".getBytes(StandardCharsets.UTF_8));

        assertNull(result.getException(), "a body shorter than the header must be stored");
        assertEquals("short", stored("short.txt"));
    }

    @Test
    void fileBodyIsStoredWithItsLength() throws Exception {
        File file = Files.writeString(dir.resolve("data.txt"), "the content of the file").toFile();

        Exchange result = put("file.txt", 3L, file);

        assertNull(result.getException());
        assertEquals("the content of the file", stored("file.txt"));
    }

    @Test
    void streamOfUnknownLengthUsesTheContentLengthHeader() {
        // control: the case the header is for, a stream whose length is not known
        byte[] data = "a stream of unknown length".getBytes(StandardCharsets.UTF_8);
        InputStream stream = new FilterInputStream(new ByteArrayInputStream(data)) {
        };

        Exchange result = put("stream.txt", (long) data.length, stream);

        assertNull(result.getException());
        assertEquals("a stream of unknown length", stored("stream.txt"));
    }

    private Exchange put(String key, long contentLengthHeader, Object body) {
        return template.send("direct:put", exchange -> {
            exchange.getIn().setHeader(MinioConstants.OBJECT_NAME, key);
            exchange.getIn().setHeader(MinioConstants.CONTENT_LENGTH, contentLengthHeader);
            exchange.getIn().setBody(body);
        });
    }

    private String stored(String key) {
        byte[] content = s3.content(key);
        return content != null ? new String(content, StandardCharsets.UTF_8) : null;
    }
}
