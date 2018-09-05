/**
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
package org.apache.camel.component.http4;

import java.net.ConnectException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.http4.handler.BasicValidationHandler;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @version
 */
public class HttpNoConnectionTest extends BaseHttpTest {

    private HttpServer localServer;
    
    @Before
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().
                setHttpProcessor(getBasicHttpProcessor()).
                setConnectionReuseStrategy(getConnectionReuseStrategy()).
                setResponseFactory(getHttpResponseFactory()).
                setExpectationVerifier(getHttpExpectationVerifier()).
                setSslContext(getSSLContext()).
                registerHandler("/search", new BasicValidationHandler("GET", null, null, getExpectedContent())).create();
        localServer.start();

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
        Exchange exchange = template.request("http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "/search", new Processor() {
            public void process(Exchange exchange) throws Exception {
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void httpConnectionNotOk() throws Exception {
        String url = "http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "/search";
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
