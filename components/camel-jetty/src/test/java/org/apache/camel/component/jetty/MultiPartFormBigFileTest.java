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
package org.apache.camel.component.jetty;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import jakarta.activation.DataHandler;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.IOHelper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiPartFormBigFileTest extends BaseJettyTest {

    @TempDir
    File tempDir;

    private HttpEntity createMultipartRequestEntityWithBigFile() {
        return MultipartEntityBuilder.create()
                .addTextBody("comment", "A binary file of some kind").build();

    }

    @Test
    void testSendMultiPartFormWithBigFile() throws Exception {
        HttpPost post = new HttpPost("http://localhost:" + getPort() + "/test");
        post.setEntity(createMultipartRequestEntityWithBigFile());
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(post)) {
            int status = response.getCode();

            assertEquals(200, status, "Get a wrong response status");
            String result = IOHelper.loadText(response.getEntity().getContent()).trim();

            assertEquals(Integer.toString("A binary file of some kind".length()), result, "Get a wrong result");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                fromF("jetty://http://localhost:{{port}}/test?filesLocation=%s&fileSizeThreshold=1",
                        tempDir.getAbsolutePath())
                        .process(new Processor() {

                            public void process(Exchange exchange) {
                                AttachmentMessage in = exchange.getIn(AttachmentMessage.class);
                                assertEquals(1, in.getAttachments().size(), "Get a wrong attachment size");
                                // The file name is attachment id
                                DataHandler data = in.getAttachment("comment");
                                assertNotNull(data, "Should get the DataHandle comment");
                                assertTrue(tempDir.exists());
                                int received = 0;
                                try (InputStream files = data.getInputStream()) {
                                    byte[] buffer = new byte[256];
                                    int b;
                                    while ((b = files.read(buffer)) != -1) {
                                        received += b;
                                    }
                                    exchange.getMessage().setBody(Integer.toString(received));
                                } catch (IOException ex) {
                                    throw new RuntimeException(ex);
                                }
                            }

                        });
            }
        };
    }
}
