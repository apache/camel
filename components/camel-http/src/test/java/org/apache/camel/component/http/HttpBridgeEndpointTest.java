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
import org.apache.camel.component.http.handler.BasicRawQueryValidationHandler;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.Exchange.*;
import static org.apache.camel.http.common.HttpMethods.GET;

public class HttpBridgeEndpointTest extends BaseHttpTest {

    private HttpServer localServer;
    private String url;

    @Before
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().
                setHttpProcessor(getBasicHttpProcessor()).
                setConnectionReuseStrategy(getConnectionReuseStrategy()).
                setResponseFactory(getHttpResponseFactory()).
                setExpectationVerifier(getHttpExpectationVerifier()).
                setSslContext(getSSLContext()).
                registerHandler("/", new BasicValidationHandler(GET.name(), null, null, getExpectedContent())).
                registerHandler("/query", new BasicRawQueryValidationHandler(GET.name(), "x=%3B", null, getExpectedContent())).create();
        localServer.start();

        url = "http://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort();

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
    public void notBridgeEndpoint() throws Exception {
        Exchange exchange = template.request("http://host/?bridgeEndpoint=false", exchange1 -> exchange1.getIn().setHeader(HTTP_URI, url + "/"));

        assertExchange(exchange);
    }

    @Test
    public void bridgeEndpoint() throws Exception {
        Exchange exchange = template.request(url + "/?bridgeEndpoint=true", exchange1 -> exchange1.getIn().setHeader(HTTP_URI, "http://host:8080/"));

        assertExchange(exchange);
    }

    @Test
    public void bridgeEndpointWithQuery() throws Exception {
        Exchange exchange = template.request(url + "/query?bridgeEndpoint=true", exchange1 -> {
            exchange1.getIn().setHeader(HTTP_URI, "http://host:8080/");
            exchange1.getIn().setHeader(HTTP_QUERY, "x=%3B");
        });

        assertExchange(exchange);
    }

    @Test
    public void bridgeEndpointWithRawQueryAndQuery() throws Exception {
        Exchange exchange = template.request(url + "/query?bridgeEndpoint=true", exchange1 -> {
            exchange1.getIn().setHeader(HTTP_URI, "http://host:8080/");
            exchange1.getIn().setHeader(HTTP_RAW_QUERY, "x=%3B");
            exchange1.getIn().setHeader(HTTP_QUERY, "x=;");
        });

        assertExchange(exchange);
    }

    @Test
    public void unsafeCharsInHttpURIHeader() throws Exception {
        Exchange exchange = template.request(url + "/?bridgeEndpoint=true", exchange1 -> exchange1.getIn().setHeader(HTTP_URI, "/<>{}"));

        assertNull(exchange.getException());
        assertExchange(exchange);
    }

}
