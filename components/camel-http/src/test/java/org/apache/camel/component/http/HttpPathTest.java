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
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.GET;

public class HttpPathTest extends BaseHttpTest {

    private HttpServer localServer;

    private String endpointUrl;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/search", new BasicValidationHandler(GET.name(), null, null, getExpectedContent()))
                .register("/test%20/path", new BasicValidationHandler(GET.name(), null, null, getExpectedContent()))
                .register("/testWithQueryParams",
                        new BasicValidationHandler(GET.name(), "abc=123", null, getExpectedContent()))
                .create();
        localServer.start();

        endpointUrl = "http://localhost:" + localServer.getLocalPort();

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
    public void httpPath() {
        Exchange exchange = template.request(endpointUrl + "/search", exchange1 -> {
        });

        assertExchange(exchange);
    }

    @Test
    public void httpPathHeader() {
        Exchange exchange
                = template.request(endpointUrl + "/", exchange1 -> exchange1.getIn().setHeader(Exchange.HTTP_PATH, "search"));

        assertExchange(exchange);
    }

    @Test
    public void httpPathHeaderWithStaticQueryParams() {
        Exchange exchange = template.request(endpointUrl + "?abc=123",
                exchange1 -> exchange1.getIn().setHeader(Exchange.HTTP_PATH, "testWithQueryParams"));

        assertExchange(exchange);
    }

    @Test
    public void httpPathHeaderWithBaseSlashesAndWithStaticQueryParams() {
        Exchange exchange = template.request(endpointUrl + "/" + "?abc=123",
                exchange1 -> exchange1.getIn().setHeader(Exchange.HTTP_PATH, "/testWithQueryParams"));

        assertExchange(exchange);
    }

    @Test
    public void httpEscapedCharacters() {
        Exchange exchange = template.request(endpointUrl + "/test%20/path", exchange1 -> {
        });

        assertExchange(exchange);
    }
}
