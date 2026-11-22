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
package org.apache.camel.component.http;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A message header whose name is not a valid HTTP header name, such as a form field without = that a consumer mapped to
 * a header, is not sent with the request, which the server would reject.
 */
public class HttpProducerInvalidHeaderNameTest extends BaseHttpTest {

    private static final String TEXT = "Hi, I'm alice@example.com. My order is late, can you refund it?";

    private HttpServer localServer;

    @Override
    public void setupResources() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost").setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/myapp", (request, response, context) -> {
                    // reply with the names of the received headers that start with x- or hi, separated by |
                    String body = Arrays.stream(request.getHeaders())
                            .map(header -> header.getName().toLowerCase())
                            .filter(name -> name.startsWith("x-") || name.startsWith("hi"))
                            .sorted()
                            .collect(Collectors.joining("|"));
                    response.setEntity(new StringEntity(body, StandardCharsets.US_ASCII));
                    response.setCode(HttpStatus.SC_OK);
                }).create();
        localServer.start();
    }

    @Override
    public void cleanupResources() throws Exception {
        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    public void testInvalidHeaderNameIsNotSent() {
        String uri = "http://localhost:" + localServer.getLocalPort() + "/myapp";
        String body = template.requestBodyAndHeaders(uri, null, Map.of(TEXT, "", "X-Valid", "value"), String.class);

        assertEquals("x-valid", body);
    }
}
