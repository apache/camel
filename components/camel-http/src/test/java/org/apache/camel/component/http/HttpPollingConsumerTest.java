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

import java.net.SocketTimeoutException;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.http.handler.DelayValidationHandler;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.GET;
import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HttpPollingConsumerTest extends BaseHttpTest {

    private HttpServer localServer;
    private final String user = "camel";
    private final String password = "password";
    private String endpointUrl;

    @Override
    public void setupResources() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost").setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/", new DelayValidationHandler(GET.name(), null, null, getExpectedContent(), 1000)).create();
        localServer.start();

        endpointUrl = "http://localhost:" + localServer.getLocalPort();
    }

    @Override
    public void cleanupResources() throws Exception {

        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    public void basicAuthenticationShouldSuccess() {
        String body = consumer.receiveBody(endpointUrl + "/?authUsername=" + user + "&authPassword="
                                           + password,
                String.class);
        assertEquals(getExpectedContent(), body);

    }

    @Test
    public void basicAuthenticationPreemptiveShouldSuccess() {

        String body = consumer.receiveBody(endpointUrl + "/?authUsername=" + user + "&authPassword="
                                           + password + "&authenticationPreemptive=true",
                String.class);
        assertEquals(getExpectedContent(), body);
    }

    @Test
    public void testReceive() {
        String body = consumer.receiveBody(endpointUrl + "/", String.class);
        assertEquals(getExpectedContent(), body);
    }

    @Test
    public void testReceiveTimeout() {
        String body = consumer.receiveBody(endpointUrl + "/", 5000, String.class);
        assertEquals(getExpectedContent(), body);
    }

    @Test
    public void testReceiveTimeoutTriggered() {
        RuntimeCamelException ex = assertThrows(RuntimeCamelException.class,
                () -> consumer.receiveBody(endpointUrl + "/", 250, String.class),
                "Should have thrown exception");
        assertIsInstanceOf(SocketTimeoutException.class, ex.getCause());
    }
}
