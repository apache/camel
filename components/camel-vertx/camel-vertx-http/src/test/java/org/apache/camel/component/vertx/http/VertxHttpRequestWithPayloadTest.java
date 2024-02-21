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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.ext.web.multipart.MultipartForm;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.vertx.http.VertxHttpConstants.CONTENT_TYPE_FORM_URLENCODED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertxHttpRequestWithPayloadTest extends VertxHttpTestSupport {

    private static final String BODY_PAYLOAD = "Some Body";

    @Test
    public void testPostBodyAsString() {
        String result = template.requestBody(getProducerUri(), BODY_PAYLOAD, String.class);
        assertEquals("Got body: " + BODY_PAYLOAD, result);
    }

    @Test
    public void testPostBodyAsByteArray() {
        String result = template.requestBody(getProducerUri(), BODY_PAYLOAD.getBytes(StandardCharsets.UTF_8), String.class);
        assertEquals("Got body: " + BODY_PAYLOAD, result);
    }

    @Test
    public void testPostBodyAsBuffer() {
        String result = template.requestBody(getProducerUri(), Buffer.buffer(BODY_PAYLOAD), String.class);
        assertEquals("Got body: " + BODY_PAYLOAD, result);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testPostBodyAsMultiMap() throws Exception {
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.set("organization", "Apache");
        form.set("project", "Camel");

        Map<String, String> expectedBody = new HashMap<>();

        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        form.entries().forEach(entry -> expectedBody.put(entry.getKey(), entry.getValue()));
        mockEndpoint.expectedBodiesReceived(expectedBody);

        Exchange result = template.request(getProducerUri() + "/form", exchange -> exchange.getMessage().setBody(form));

        Message message = result.getMessage();
        assertEquals(CONTENT_TYPE_FORM_URLENCODED, message.getHeader(Exchange.CONTENT_TYPE));
        assertEquals("Apache", message.getHeader("organization"));
        assertEquals("Camel", message.getHeader("project"));

        mockEndpoint.assertIsSatisfied();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testPostBodyAsFormUrlEncoded() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedHeaderReceived("organization", "Apache");
        mockEndpoint.expectedHeaderReceived("project", "Camel");

        template.sendBodyAndHeader(getProducerUri() + "/form", "organization=Apache&project=Camel", Exchange.CONTENT_TYPE,
                CONTENT_TYPE_FORM_URLENCODED);

        mockEndpoint.assertIsSatisfied();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testPostBodyAsMultipartFormUpload() throws IOException {
        String fileContent = "Test Vert.x HTTP file upload";
        Path tmpFilePath = Files.createTempFile("camel-vertx-http", null);
        Files.write(tmpFilePath, fileContent.getBytes(StandardCharsets.UTF_8));
        File file = tmpFilePath.toFile();

        MultipartForm form = MultipartForm.create();
        form.binaryFileUpload("test.txt", file.getName(), file.getPath(), "text/plain");

        Exchange result = template.request(getProducerUri() + "/upload", exchange -> exchange.getMessage().setBody(form));

        Message message = result.getMessage();
        assertTrue(message.getHeader(Exchange.CONTENT_TYPE, String.class).startsWith("multipart/form-data; boundary"));
        assertEquals(fileContent, message.getBody(String.class));
    }

    @Test
    public void testPostBodyAsStream() throws IOException {
        String fileContent = "Test Vert.x HTTP file upload";
        Path tmpFilePath = Files.createTempFile("camel-vertx-http", null);
        Files.write(tmpFilePath, fileContent.getBytes(StandardCharsets.UTF_8));
        File file = tmpFilePath.toFile();

        AsyncFile asyncFile = Vertx.vertx().fileSystem().openBlocking(file.getPath(), new OpenOptions());
        String result = template.requestBody(getProducerUri() + "/stream", asyncFile, String.class);
        assertEquals(fileContent, result);
    }

    @Test
    public void testPostBodyAsUnknownType() {
        assertThrows(CamelExecutionException.class, () -> {
            template.sendBody(getProducerUri(), 12345);
        });
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getTestServerUri())
                        .choice()
                        .when(simple("${body} == ''"))
                        .setBody(constant("Got empty body"))
                        .otherwise()
                        .setBody(simple("Got body: ${body}"));

                from(getTestServerUri() + "/form")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) {
                                exchange.toString();
                            }
                        })
                        .to("mock:result");

                from(getTestServerUri() + "/upload")
                        .process(exchange -> {
                            AttachmentMessage in = exchange.getMessage(AttachmentMessage.class);
                            InputStream inputStream = in.getAttachment("test.txt").getInputStream();
                            String fileContent = getContext().getTypeConverter().convertTo(String.class, inputStream);
                            exchange.getMessage().setBody(fileContent);
                        });

                from(getTestServerUri() + "/stream")
                        .log("foo");
            }
        };
    }
}
