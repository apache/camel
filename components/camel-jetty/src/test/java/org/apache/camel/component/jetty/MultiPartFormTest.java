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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MultiPartFormTest extends BaseJettyTest {
    private HttpEntity createMultipartRequestEntity() {
        File file = new File("src/test/resources/log4j2.properties");
        return MultipartEntityBuilder.create().addTextBody("comment", "A binary file of some kind")
                .addBinaryBody(file.getName(), file).build();

    }

    @Test
    public void testSendMultiPartForm() throws Exception {
        HttpPost post = new HttpPost("http://localhost:" + getPort() + "/test");
        post.setEntity(createMultipartRequestEntity());
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(post)) {
            int status = response.getCode();

            assertEquals(200, status, "Get a wrong response status");
            String result = IOHelper.loadText(response.getEntity().getContent()).trim();

            assertEquals("A binary file of some kind", result, "Get a wrong result");
        }
    }

    @Test
    public void testSendMultiPartFormFromCamelHttpComponnent() {
        String result
                = template.requestBody("http://localhost:" + getPort() + "/test", createMultipartRequestEntity(), String.class);
        assertEquals("A binary file of some kind", result, "Get a wrong result");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: e1
                // Set the jetty temp directory which store the file for multi
                // part form
                // camel-jetty will clean up the file after it handled the
                // request.
                // The option works rightly from Camel 2.4.0
                getContext().getGlobalOptions().put("CamelJettyTempDir", "target");

                from("jetty://http://localhost:{{port}}/test").process(new Processor() {

                    public void process(Exchange exchange) throws Exception {
                        AttachmentMessage in = exchange.getIn(AttachmentMessage.class);
                        assertEquals(2, in.getAttachments().size(), "Get a wrong attachement size");
                        // The file name is attachment id
                        DataHandler data = in.getAttachment("log4j2.properties");

                        assertNotNull(data, "Should get the DataHandle log4j2.properties");
                        // This assert is wrong, but the correct content-type
                        // (application/octet-stream)
                        // will not be returned until Jetty makes it available -
                        // currently the content-type
                        // returned is just the default for FileDataHandler (for
                        // the implentation being used)
                        // assertEquals("Get a wrong content type",
                        // "text/plain", data.getContentType());
                        assertEquals("log4j2.properties", data.getName(), "Got the wrong name");

                        assertTrue(data.getDataSource().getInputStream().read() != -1,
                                "We should get the data from the DataHandle");

                        // The other form date can be get from the message
                        // header

                        // For binary attachment, header should also be
                        // populated by DataHandler but not payload
                        Object header = in.getHeader("log4j2.properties");
                        assertEquals(DataHandler.class, header.getClass());
                        assertEquals(data, header);

                        exchange.getMessage().setBody(in.getHeader("comment"));
                    }

                });
                // END SNIPPET: e1
            }
        };
    }

}
