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

import java.util.concurrent.CountDownLatch;

import org.apache.camel.Exchange;
import org.apache.camel.component.http.handler.BlockingValidationHandler;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.GET;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class HttpSOTimeoutTest extends BaseHttpTest {

    private HttpServer localServer;

    // the server only answers once the latch is released: tests expecting a timeout never release it before the call
    private CountDownLatch serverResponse;

    @Override
    public void setupResources() throws Exception {
        serverResponse = new CountDownLatch(1);
        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost").setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/",
                        new BlockingValidationHandler(GET.name(), null, null, getExpectedContent(), serverResponse))
                .create();
        localServer.start();
    }

    @Override
    public void cleanupResources() {
        // unblock any server thread still waiting
        serverResponse.countDown();
        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    public void httpGet() {
        serverResponse.countDown();
        Exchange exchange = template.request("http://localhost:"
                                             + localServer.getLocalPort() + "?httpClient.responseTimeout=5000",
                exchange1 -> {
                });

        assertExchange(exchange);
    }

    @Test
    public void httpGetShouldThrowASocketTimeoutException() {
        Exchange reply = template.request("http://localhost:"
                                          + localServer.getLocalPort() + "?httpClient.responseTimeout=200",
                exchange -> {
                });
        Exception e = reply.getException();
        assertNotNull(e, "Should have thrown an exception");
    }

    @Test
    public void httpGetUriOption() {
        serverResponse.countDown();
        HttpEndpoint endpoint = context.getEndpoint("http://localhost:"
                                                    + localServer.getLocalPort() + "?responseTimeout=5000",
                HttpEndpoint.class);
        Exchange exchange = template.request(endpoint,
                exchange1 -> {
                });

        assertExchange(exchange);

        Assertions.assertEquals(5000, endpoint.getResponseTimeout());
    }

    @Test
    public void httpGetUriOptionShouldThrowASocketTimeoutException() {
        Exchange reply = template.request("http://localhost:"
                                          + localServer.getLocalPort() + "?responseTimeout=200",
                exchange -> {
                });
        Exception e = reply.getException();
        assertNotNull(e, "Should have thrown an exception");
    }

}
