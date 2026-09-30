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
package org.apache.camel.component.vertx.http;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.activation.DataHandler;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A file body that is not a local {@link java.io.File}, such as a file of the ftp or sftp consumers without a
 * localWorkDirectory whose content is in memory, is sent with its content.
 */
public class VertxHttpRemoteFileBodyTest extends VertxHttpTestSupport {

    @Test
    public void testRemoteFileBody() throws Exception {
        Exchange out = send(getProducerUri() + "/echo", false, true);

        assertFalse(out.isFailed(), "Should not fail");
        assertEquals("Hello World", out.getMessage().getBody(String.class));
    }

    @Test
    public void testRemoteFileStreamBody() throws Exception {
        // streamDownload=true: the content is an InputStream
        Exchange out = send(getProducerUri() + "/echo", true, true);

        assertFalse(out.isFailed(), "Should not fail");
        assertEquals("Hello World", out.getMessage().getBody(String.class));
    }

    @Test
    public void testRemoteFileBodyMultipartUpload() throws Exception {
        Exchange out = send(getProducerUri() + "/upload?multipartUpload=true&multipartUploadName=cheese", false, true);

        assertFalse(out.isFailed(), "Should not fail");
        assertEquals("hello.txt=Hello World", out.getMessage().getBody(String.class));
    }

    @Test
    public void testRemoteFileStreamBodyMultipartUpload() throws Exception {
        Exchange out = send(getProducerUri() + "/upload?multipartUpload=true&multipartUploadName=cheese", true, true);

        assertFalse(out.isFailed(), "Should not fail");
        assertEquals("hello.txt=Hello World", out.getMessage().getBody(String.class));
    }

    @Test
    public void testRemoteFileBodyMultipartUploadFileNameFromFile() throws Exception {
        // without the CamelFileNameOnly header the name of the file is used, not the form field name
        Exchange out = send(getProducerUri() + "/upload?multipartUpload=true&multipartUploadName=cheese", false, false);

        assertFalse(out.isFailed(), "Should not fail");
        assertEquals("hello.txt=Hello World", out.getMessage().getBody(String.class));
    }

    private Exchange send(String uri, boolean stream, boolean fileNameHeader) throws Exception {
        CompletableFuture<Exchange> future = template.asyncSend(uri, exchange -> {
            exchange.getMessage().setBody(createRemoteFile(stream));
            if (fileNameHeader) {
                exchange.getMessage().setHeader(Exchange.FILE_NAME_ONLY, "hello.txt");
            }
        });
        // the exchange must complete, without the content being sent it never did
        return future.get(10, TimeUnit.SECONDS);
    }

    private static GenericFile<Object> createRemoteFile(boolean stream) {
        // like a remote file: the file handle is not a java.io.File, and the content is downloaded in memory
        // (byte[]), or streamed (an InputStream, with streamDownload=true)
        GenericFile<Object> file = new GenericFile<>();
        file.setFile(new Object());
        file.setFileNameOnly("hello.txt");
        file.setFileName("hello.txt");
        byte[] content = "Hello World".getBytes(StandardCharsets.UTF_8);
        file.setBody(stream ? new ByteArrayInputStream(content) : content);
        return file;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getTestServerUri() + "/echo")
                        .convertBodyTo(String.class);

                from(getTestServerUri() + "/upload")
                        .process(exchange -> {
                            // undertow stores the multipart form as a map in the message
                            DataHandler dh = (DataHandler) exchange.getMessage().getBody(Map.class).get("cheese");
                            String content = new String(
                                    dh.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                            exchange.getMessage().setBody(dh.getDataSource().getName() + "=" + content);
                        });
            }
        };
    }
}
