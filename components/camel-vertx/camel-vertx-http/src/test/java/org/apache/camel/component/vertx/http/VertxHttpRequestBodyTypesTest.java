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

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.apache.camel.component.vertx.http.VertxHttpConstants.CONTENT_TYPE_FORM_URLENCODED;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The request bodies the producer reads: a Path, a Reader and a URL encoded form.
 */
public class VertxHttpRequestBodyTypesTest extends VertxHttpTestSupport {

    private static final String BODY_PAYLOAD = "Some Body";

    @TempDir
    Path tempDir;

    @Test
    public void testPostBodyAsPath() throws IOException {
        Path file = tempDir.resolve("body.txt");
        Files.writeString(file, BODY_PAYLOAD, StandardCharsets.UTF_8);

        String result = template.requestBody(getProducerUri(), file, String.class);
        assertEquals("Got body: " + BODY_PAYLOAD, result);
    }

    @Test
    public void testPostBodyAsReader() {
        String result = template.requestBody(getProducerUri(), new StringReader(BODY_PAYLOAD), String.class);
        assertEquals("Got body: " + BODY_PAYLOAD, result);
    }

    @Test
    public void testPostFormUrlEncodedValuesAreDecoded() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:form");
        mockEndpoint.expectedHeaderReceived("q", "a&b");
        mockEndpoint.expectedHeaderReceived("name", "Camel Rider");

        template.sendBodyAndHeader(getProducerUri() + "/form", "q=a%26b&name=Camel+Rider", Exchange.CONTENT_TYPE,
                CONTENT_TYPE_FORM_URLENCODED);

        mockEndpoint.assertIsSatisfied();
    }

    @Test
    public void testPostFormUrlEncodedRepeatedName() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:form");
        mockEndpoint.expectedHeaderReceived("project", new ArrayList<>(List.of("Camel", "Kamelets")));

        template.sendBodyAndHeader(getProducerUri() + "/form", "project=Camel&project=Kamelets", Exchange.CONTENT_TYPE,
                CONTENT_TYPE_FORM_URLENCODED);

        mockEndpoint.assertIsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getTestServerUri())
                        .setBody(simple("Got body: ${body}"));

                from(getTestServerUri() + "/form")
                        .to("mock:form");
            }
        };
    }
}
