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
package org.apache.camel.component.cometd;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class CometdCrossOriginFilterTest extends CamelTestSupport {

    private static final String ORIGIN = "https://app.example";

    @RegisterExtension
    AvailablePortFinder.Port defaultOriginsPort = AvailablePortFinder.find();
    @RegisterExtension
    AvailablePortFinder.Port anyOriginPort = AvailablePortFinder.find();
    @RegisterExtension
    AvailablePortFinder.Port wildcardPatternPort = AvailablePortFinder.find();
    @RegisterExtension
    AvailablePortFinder.Port namedOriginPort = AvailablePortFinder.find();

    @Test
    void testDefaultOriginsDoNotAllowCredentials() throws Exception {
        assertNoCredentials(defaultOriginsPort.getPort());
    }

    @Test
    void testAnyOriginDoesNotAllowCredentials() throws Exception {
        assertNoCredentials(anyOriginPort.getPort());
    }

    @Test
    void testWildcardPatternDoesNotAllowCredentials() throws Exception {
        assertNoCredentials(wildcardPatternPort.getPort());
    }

    @Test
    void testNamedOriginAllowsCredentials() throws Exception {
        HttpResponse<String> response = post(namedOriginPort.getPort());
        assertEquals(ORIGIN, response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
        assertEquals("true", response.headers().firstValue("Access-Control-Allow-Credentials").orElse(null));
    }

    private void assertNoCredentials(int port) throws Exception {
        HttpResponse<String> response = post(port);
        // the origin is still allowed, but without credentials
        assertEquals(ORIGIN, response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
        assertFalse(response.headers().firstValue("Access-Control-Allow-Credentials").isPresent());
    }

    private HttpResponse<String> post(int port) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/cometd"))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "[{\"channel\":\"/meta/handshake\",\"version\":\"1.0\",\"supportedConnectionTypes\":[\"long-polling\"]}]"))
                .header("Content-Type", "application/json")
                .header("Origin", ORIGIN)
                .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String options = "?crossOriginFilterOn=true&filterPath=/cometd/*";
                from("cometd://127.0.0.1:" + defaultOriginsPort.getPort() + "/service/test" + options)
                        .to("mock:default");
                from("cometd://127.0.0.1:" + anyOriginPort.getPort() + "/service/test" + options + "&allowedOrigins=*")
                        .to("mock:any");
                from("cometd://127.0.0.1:" + wildcardPatternPort.getPort() + "/service/test" + options
                     + "&allowedOrigins=https://*")
                        .to("mock:pattern");
                from("cometd://127.0.0.1:" + namedOriginPort.getPort() + "/service/test" + options
                     + "&allowedOrigins=" + ORIGIN)
                        .to("mock:named");
            }
        };
    }
}
