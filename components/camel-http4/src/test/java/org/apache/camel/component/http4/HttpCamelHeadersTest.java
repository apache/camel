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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.http4.handler.HeaderValidationHandler;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.protocol.HttpContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @version
 */
public class HttpCamelHeadersTest extends BaseHttpTest {

    protected HttpServer localServer;

    @Before
    @Override
    public void setUp() throws Exception {
        Map<String, String> expectedHeaders = new HashMap<String, String>();
        expectedHeaders.put("TestHeader", "test");
        expectedHeaders.put("Accept-Language", "pl");

        localServer = ServerBootstrap.bootstrap().
                setHttpProcessor(getBasicHttpProcessor()).
                setConnectionReuseStrategy(getConnectionReuseStrategy()).
                setResponseFactory(getHttpResponseFactory()).
                setExpectationVerifier(getHttpExpectationVerifier()).
                setSslContext(getSSLContext()).
                registerHandler("/", new MyHeaderValidationHandler("GET", "HTTP/1.0", getExpectedContent(), expectedHeaders)).create();
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
    public void httpHeadersShouldPresent() throws Exception {
        assertExchange(doExchange());
    }

    @Override
    protected void assertHeaders(Map<String, Object> headers) {
        super.assertHeaders(headers);

        assertEquals("test", headers.get("TestHeader"));
        assertEquals("pl", headers.get("Accept-Language"));
    }

    private Exchange doExchange() {
        return template.request(
                "http4://"
                        + localServer.getInetAddress().getHostName()
                        + ":"
                        + localServer.getLocalPort()
                        + "/"
                        + setupEndpointParams(),
                new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getIn().setHeader("TestHeader", "test");
                        exchange.getIn().setHeader("Accept-Language", "pl");
                        exchange.getIn().setHeader(Exchange.HTTP_PROTOCOL_VERSION, "HTTP/1.0");
                    }
                }
        );
    }

    protected String setupEndpointParams() {
        return "";
    }

    private class MyHeaderValidationHandler extends HeaderValidationHandler {
        private String expectProtocolVersion;

        MyHeaderValidationHandler(String expectedMethod, String protocolVersion,
                                         String responseContent, Map<String, String> expectedHeaders) {
            super(expectedMethod, null, null, responseContent, expectedHeaders);
            expectProtocolVersion = protocolVersion;
        }

        public void handle(final HttpRequest request, final HttpResponse response,
                           final HttpContext context) throws HttpException, IOException {
            if (!expectProtocolVersion.equals(request.getProtocolVersion().toString())) {
                response.setStatusCode(HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED);
                return;
            }
            super.handle(request, response, context);
        }

    }
}