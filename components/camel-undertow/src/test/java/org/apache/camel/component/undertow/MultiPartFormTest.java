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
package org.apache.camel.component.undertow;

import java.io.File;
import java.util.Map;

import jakarta.activation.DataHandler;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MultiPartFormTest extends BaseUndertowTest {

    private HttpEntity createMultipartRequestEntity() {
        File file = new File("src/test/resources/log4j2.properties");
        return MultipartEntityBuilder.create()
                .addTextBody("comment", "A binary file of some kind")
                .addBinaryBody(file.getName(), file)
                .build();
    }

    @Test
    public void testSendMultiPartForm() throws Exception {
        HttpPost post = new HttpPost("http://localhost:" + getPort() + "/test");
        post.setEntity(createMultipartRequestEntity());
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(post)) {
            int status = response.getCode();

            assertEquals(200, status, "Get a wrong response status");
            String result = IOHelper.loadText(response.getEntity().getContent()).trim();

            assertEquals("A binary file of some kind", result, "Get a wrong result");
        }
    }

    @Test
    public void testSendMultiPartFormFromCamelHttpComponent() {
        String result
                = template.requestBody("http://localhost:" + getPort() + "/test", createMultipartRequestEntity(), String.class);
        assertEquals("A binary file of some kind", result, "Get a wrong result");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("undertow://http://localhost:{{port}}/test").process(exchange -> {
                    AttachmentMessage in = exchange.getIn(AttachmentMessage.class);
                    assertEquals(1, in.getAttachments().size(), "Get a wrong attachement size");
                    // The file name is attachment id
                    DataHandler data = in.getAttachment("log4j2.properties");

                    assertNotNull(data, "Should get the DataHandler log4j2.properties");
                    assertEquals("log4j2.properties", data.getName(), "Got the wrong name");

                    assertTrue(data.getDataSource().getInputStream().available() > 0,
                            "We should get the data from the DataHandler");

                    // form data should also be available as a body
                    Map body = in.getBody(Map.class);
                    assertEquals("A binary file of some kind", body.get("comment"));
                    assertEquals(data, body.get("log4j2.properties"));
                    exchange.getMessage().setBody(in.getHeader("comment"));
                });
                // END SNIPPET: e1
            }
        };
    }

}
