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
import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HttpNoCamelHeaderTest extends BaseHttpTest {

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
                registerHandler("/hello", (request, response, context) -> {
                    response.setStatusCode(HttpStatus.SC_OK);
                    Object header = request.getFirstHeader(Exchange.FILE_NAME);
                    assertNull("There should be no Camel header", header);

                    for (Header h : request.getAllHeaders()) {
                        if (h.getName().startsWith("Camel") || h.getName().startsWith("org.apache.camel")) {
                            assertNull("There should be no Camel header", h);
                        }
                    }

                    // set ar regular and Camel header
                    response.setHeader("MyApp", "dude");
                    response.setHeader(Exchange.TO_ENDPOINT, "foo");
                }).create();
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
    public void testNoCamelHeader() throws Exception {
        Exchange out = template.request("http://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "/hello", exchange -> {
            exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "text/plain");
            exchange.getIn().setHeader(Exchange.FILE_NAME, "hello.txt");
            exchange.getIn().setBody("This is content");
        });

        assertNotNull(out);
        assertFalse("Should not fail", out.isFailed());
        assertEquals("dude", out.getMessage().getHeader("MyApp"));
        assertNull(out.getMessage().getHeader(Exchange.TO_ENDPOINT));
    }
}
