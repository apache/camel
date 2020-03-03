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

import java.net.ConnectException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.http.common.HttpMethods.GET;

public class HttpNoConnectionTest extends BaseHttpTest {

    private HttpServer localServer;

    private String endpointUrl;

    @Before
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().
                setHttpProcessor(getBasicHttpProcessor()).
                setConnectionReuseStrategy(getConnectionReuseStrategy()).
                setResponseFactory(getHttpResponseFactory()).
                setExpectationVerifier(getHttpExpectationVerifier()).
                setSslContext(getSSLContext()).
                registerHandler("/search", new BasicValidationHandler(GET.name(), null, null, getExpectedContent())).create();
        localServer.start();

        endpointUrl = "http://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort();

        super.setUp();
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
    public void httpConnectionOk() throws Exception {
        Exchange exchange = template.request(endpointUrl + "/search", exchange1 -> {
        });

        assertExchange(exchange);
    }

    @Test
    public void httpConnectionNotOk() throws Exception {
        String url = endpointUrl + "/search";
        // stop server so there are no connection
        localServer.stop();
        localServer.awaitTermination(1000, TimeUnit.MILLISECONDS);

        Exchange reply = template.request(url, null);
        Exception e = reply.getException();
        assertNotNull("Should have thrown an exception", e);
        ConnectException cause = assertIsInstanceOf(ConnectException.class, e);
        assertTrue(cause.getMessage().contains("failed"));
    }

}
