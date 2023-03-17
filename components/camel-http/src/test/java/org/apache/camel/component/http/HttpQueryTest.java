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

public class HttpQueryTest extends BaseHttpTest {

    private HttpServer localServer;

    private String baseUrl;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();

        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/", new BasicValidationHandler(GET.name(), "hl=en&q=camel", null, getExpectedContent()))
                .register("/test/", new BasicValidationHandler(GET.name(), "my=@+camel", null, getExpectedContent()))
                .register("/user/pass",
                        new BasicValidationHandler(GET.name(), "password=baa&username=foo", null, getExpectedContent()))
                .create();
        localServer.start();

        baseUrl = "http://localhost:" + localServer.getLocalPort();
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
    public void httpQuery() throws Exception {
        Exchange exchange = template.request(baseUrl + "/?hl=en&q=camel", exchange1 -> {
        });

        assertExchange(exchange);
    }

    @Test
    public void httpQueryHeader() throws Exception {
        Exchange exchange = template.request(baseUrl + "/",
                exchange1 -> exchange1.getIn().setHeader(Exchange.HTTP_QUERY, "hl=en&q=camel"));

        assertExchange(exchange);
    }

    @Test
    public void httpQueryWithEscapedCharacter() throws Exception {
        Exchange exchange = template.request(baseUrl + "/test/?my=%40%20camel", exchange1 -> {
        });

        assertExchange(exchange);
    }

    @Test
    public void httpQueryWithUsernamePassword() throws Exception {
        Exchange exchange = template.request(baseUrl + "/user/pass?password=baa&username=foo", exchange1 -> {
        });

        assertExchange(exchange);
    }
}
