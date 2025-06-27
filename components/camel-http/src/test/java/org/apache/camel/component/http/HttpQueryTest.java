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
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.GET;

public class HttpQueryTest extends BaseHttpTest {

    private HttpServer localServer;

    private String baseUrl;

    private final String DANISH_CHARACTERS_UNICODE = "\u00e6\u00f8\u00e5\u00C6\u00D8\u00C5";

    @Override
    public void setupResources() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost").setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/", new BasicValidationHandler(GET.name(), "hl=en&q=camel", null, getExpectedContent()))
                .register("/test/", new BasicValidationHandler(GET.name(), "my=@+camel", null, getExpectedContent()))
                .register("/user/pass",
                        new BasicValidationHandler(GET.name(), "password=baa&username=foo", null, getExpectedContent()))
                .register("/user/passwd",
                        new BasicValidationHandler(
                                GET.name(), "password='PasswordWithCharsThatNeedEscaping!≥≤!'&username=NotFromTheUSofA", null,
                                getExpectedContent()))
                .register("/danish-accepted",
                        new BasicValidationHandler(
                                GET.name(), "characters='" + DANISH_CHARACTERS_UNICODE + "'", null, getExpectedContent()))
                .create();
        localServer.start();

        baseUrl = "http://localhost:" + localServer.getLocalPort();
    }

    @Override
    public void cleanupResources() throws Exception {

        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    public void httpQuery() {
        Exchange exchange = template.request(baseUrl + "/?hl=en&q=camel", exchange1 -> {
        });

        assertExchange(exchange);
    }

    @Test
    public void httpQueryHeader() {
        Exchange exchange = template.request(baseUrl + "/",
                exchange1 -> exchange1.getIn().setHeader(Exchange.HTTP_QUERY, "hl=en&q=camel"));

        assertExchange(exchange);
    }

    @Test
    public void httpQueryWithEscapedCharacter() {
        Exchange exchange = template.request(baseUrl + "/test/?my=%40%20camel", exchange1 -> {
        });

        assertExchange(exchange);
    }

    @Test
    public void httpQueryWithUsernamePassword() {
        Exchange exchange = template.request(baseUrl + "/user/pass?password=baa&username=foo", exchange1 -> {
        });

        assertExchange(exchange);
    }

    @Test
    public void httpQueryWithPasswordContainingNonAsciiCharacter() {
        Exchange exchange = template.request(
                baseUrl + "/user/passwd?password='PasswordWithCharsThatNeedEscaping!≥≤!'&username=NotFromTheUSofA",
                exchange1 -> {
                });

        assertExchange(exchange);
    }

    @Test
    public void httpQueryWithPasswordContainingNonAsciiCharacterAsQueryParams() {
        Exchange exchange = template.request(baseUrl + "/user/passwd",
                exchange1 -> exchange1.getIn().setHeader(Exchange.HTTP_QUERY,
                        "password='PasswordWithCharsThatNeedEscaping!≥≤!'&username=NotFromTheUSofA"));

        assertExchange(exchange);
    }

    @Test
    public void httpDanishCharactersAcceptedInBaseURL() {
        Exchange exchange
                = template.request(baseUrl + "/danish-accepted?characters='" + DANISH_CHARACTERS_UNICODE + "'", exchange1 -> {
                });

        assertExchange(exchange);
    }

    @Test
    public void httpDanishCharactersAcceptedAsQueryParams() {
        Exchange exchange = template.request(baseUrl + "/danish-accepted",
                exchange1 -> exchange1.getIn().setHeader(Exchange.HTTP_QUERY,
                        "characters='" + DANISH_CHARACTERS_UNICODE + "'"));

        assertExchange(exchange);
    }
}
