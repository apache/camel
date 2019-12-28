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
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.component.http.HttpMethods.GET;

public class HttpPollingConsumerTest extends BaseHttpTest {

    private HttpServer localServer;
    private String user = "camel";
    private String password = "password";
    private String endpointUrl;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        localServer = ServerBootstrap.bootstrap().
                setHttpProcessor(getBasicHttpProcessor()).
                setConnectionReuseStrategy(getConnectionReuseStrategy()).
                setResponseFactory(getHttpResponseFactory()).
                setExpectationVerifier(getHttpExpectationVerifier()).
                setSslContext(getSSLContext()).
                registerHandler("/", new DelayValidationHandler(GET.name(), null, null, getExpectedContent(), 1000)).create();
        localServer.start();

        endpointUrl = "http://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    public void basicAuthenticationShouldSuccess() throws Exception {
        String body = consumer.receiveBody(endpointUrl + "/?authUsername=" + user + "&authPassword="
            + password, String.class);
        assertEquals(getExpectedContent(), body);

    }

    @Test
    public void basicAuthenticationPreemptiveShouldSuccess() throws Exception {

        String body = consumer.receiveBody(endpointUrl + "/?authUsername=" + user + "&authPassword="
                + password + "&authenticationPreemptive=true", String.class);
        assertEquals(getExpectedContent(), body);
    }

    @Test
    public void testReceive() throws Exception {
        String body = consumer.receiveBody(endpointUrl + "/", String.class);
        assertEquals(getExpectedContent(), body);
    }

    @Test
    public void testReceiveTimeout() throws Exception {
        String body = consumer.receiveBody(endpointUrl + "/", 5000, String.class);
        assertEquals(getExpectedContent(), body);
    }

    @Test
    public void testReceiveTimeoutTriggered() throws Exception {
        try {
            consumer.receiveBody(endpointUrl + "/", 250, String.class);
            fail("Should have thrown an exception");
        } catch (RuntimeCamelException e) {
            assertIsInstanceOf(SocketTimeoutException.class, e.getCause());
        }
    }
}
