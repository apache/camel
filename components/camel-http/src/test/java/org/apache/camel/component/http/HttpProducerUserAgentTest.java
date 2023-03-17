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

import org.apache.camel.Exchange;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class HttpProducerUserAgentTest extends BaseHttpTest {

    private HttpServer localServer;

    private String endpointUrl;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();

        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/agent", (request, response, context) -> {
                    String agent = request.getFirstHeader("User-Agent").getValue();
                    assertEquals("MyAgent", agent);
                    response.setEntity(new StringEntity(agent, StandardCharsets.US_ASCII));
                    response.setCode(HttpStatus.SC_OK);
                }).create();
        localServer.start();

        endpointUrl = "http://localhost:" + localServer.getLocalPort();

        HttpComponent http = context.getComponent("http", HttpComponent.class);
        http.setUserAgent("MyAgent");
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    public void testUserAgent() throws Exception {
        Exchange out = template.request(endpointUrl + "/agent", exchange -> {
            exchange.getIn().setBody("Hello World");
        });

        assertNotNull(out);
        assertFalse(out.isFailed(), "Should not fail");
        assertEquals("MyAgent", out.getMessage().getBody(String.class));
    }

}
