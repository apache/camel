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

import javax.activation.DataHandler;

import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.IOHelper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Test;

public class MultiPartFormTest extends BaseUndertowTest {

    private HttpEntity createMultipartRequestEntity() throws Exception {
        File file = new File("src/test/resources/log4j2.properties");
        return MultipartEntityBuilder.create()
                .addTextBody("comment", "A binary file of some kind")
                .addBinaryBody(file.getName(), file)
                .build();
    }

    @Test
    public void testSendMultiPartForm() throws Exception {
        org.apache.http.client.HttpClient client = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost("http://localhost:" + getPort() + "/test");
        post.setEntity(createMultipartRequestEntity());
        HttpResponse response = client.execute(post);
        int status = response.getStatusLine().getStatusCode();

        assertEquals("Get a wrong response status", 200, status);
        String result = IOHelper.loadText(response.getEntity().getContent()).trim();

        assertEquals("Get a wrong result", "A binary file of some kind", result);
    }

    @Test
    public void testSendMultiPartFormFromCamelHttpComponnent() throws Exception {
        String result = template.requestBody("http://localhost:" + getPort() + "/test", createMultipartRequestEntity(), String.class);
        assertEquals("Get a wrong result", "A binary file of some kind", result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("undertow://http://localhost:{{port}}/test").process(exchange -> {
                    AttachmentMessage in = exchange.getIn(AttachmentMessage.class);
                    assertEquals("Get a wrong attachement size", 1, in.getAttachments().size());
                    // The file name is attachment id
                    DataHandler data = in.getAttachment("log4j2.properties");

                    assertNotNull("Should get the DataHandler log4j2.properties", data);
                    assertEquals("Got the wrong name", "log4j2.properties", data.getName());

                    assertTrue("We should get the data from the DataHandler", data.getDataSource()
                        .getInputStream().available() > 0);

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
