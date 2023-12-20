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

import org.apache.camel.Exchange;
import org.apache.camel.component.http.handler.DelayValidationHandler;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.GET;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class HttpSOTimeoutTest extends BaseHttpTest {

    private HttpServer localServer;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/", new DelayValidationHandler(GET.name(), null, null, getExpectedContent(), 2000)).create();
        localServer.start();

        super.setUp();
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
    public void httpGet() {
        Exchange exchange = template.request("http://localhost:"
                                             + localServer.getLocalPort() + "?httpClient.responseTimeout=5000",
                exchange1 -> {
                });

        assertExchange(exchange);
    }

    @Test
    public void httpGetShouldThrowASocketTimeoutException() {
        Exchange reply = template.request("http://localhost:"
                                          + localServer.getLocalPort() + "?httpClient.responseTimeout=1000",
                exchange -> {
                });
        Exception e = reply.getException();
        assertNotNull(e, "Should have thrown an exception");
    }

    @Test
    public void httpGetUriOption() {
        HttpEndpoint endpoint = context.getEndpoint("http://localhost:"
                                                    + localServer.getLocalPort() + "?responseTimeout=5000",
                HttpEndpoint.class);
        Exchange exchange = template.request(endpoint,
                exchange1 -> {
                });

        assertExchange(exchange);

        Assertions.assertEquals(Timeout.ofSeconds(5), endpoint.getResponseTimeout());
    }

    @Test
    public void httpGetUriOptionShouldThrowASocketTimeoutException() {
        Exchange reply = template.request("http://localhost:"
                                          + localServer.getLocalPort() + "?responseTimeout=1000",
                exchange -> {
                });
        Exception e = reply.getException();
        assertNotNull(e, "Should have thrown an exception");
    }

}
