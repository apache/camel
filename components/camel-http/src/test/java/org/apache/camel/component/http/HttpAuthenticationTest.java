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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.http.handler.AuthenticationValidationHandler;
import org.apache.camel.component.http.interceptor.RequestBasicAuth;
import org.apache.camel.component.http.interceptor.ResponseBasicUnauthorized;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.ResponseContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.GET;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class HttpAuthenticationTest extends BaseHttpTest {

    private HttpServer localServer;

    private final String user = "camel";
    private final String password = "password";

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/search",
                        new AuthenticationValidationHandler(GET.name(), null, null, getExpectedContent(), user, password))
                .create();
        localServer.start();

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
    public void basicAuthenticationShouldSuccess() throws Exception {
        Exchange exchange = template.request("http://localhost:"
                                             + localServer.getLocalPort() + "/search?authUsername=" + user + "&authPassword="
                                             + password,
                exchange1 -> {
                });

        assertExchange(exchange);
    }

    @Test
    public void basicAuthenticationPreemptiveShouldSuccess() throws Exception {
        Exchange exchange = template.request("http://localhost:"
                                             + localServer.getLocalPort() + "/search?authUsername=" + user + "&authPassword="
                                             + password + "&authenticationPreemptive=true",
                exchange1 -> {
                });

        assertExchange(exchange);
    }

    @Test
    public void basicAuthenticationShouldFailWithoutCreds() throws Exception {
        Exchange exchange
                = template.request("http://localhost:" + localServer.getLocalPort()
                                   + "/search?throwExceptionOnFailure=false",
                        exchange1 -> {
                        });

        assertExchangeFailed(exchange);
    }

    @Test
    public void basicAuthenticationShouldFailWithWrongCreds() throws Exception {
        Exchange exchange = template
                .request("http://localhost:" + localServer.getLocalPort()
                         + "/search?throwExceptionOnFailure=false&authUsername=camel&authPassword=wrong",
                        exchange1 -> {
                        });

        assertExchangeFailed(exchange);
    }

    @Override
    protected HttpProcessor getBasicHttpProcessor() {
        List<HttpRequestInterceptor> requestInterceptors = new ArrayList<>();
        requestInterceptors.add(new RequestBasicAuth());
        List<HttpResponseInterceptor> responseInterceptors = new ArrayList<>();
        responseInterceptors.add(new ResponseContent());
        responseInterceptors.add(new ResponseBasicUnauthorized());

        return new DefaultHttpProcessor(requestInterceptors, responseInterceptors);
    }

    protected void assertExchangeFailed(Exchange exchange) {
        assertNotNull(exchange);

        Message out = exchange.getMessage();
        assertNotNull(out);

        Map<String, Object> headers = out.getHeaders();
        assertEquals(HttpStatus.SC_UNAUTHORIZED, headers.get(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("0", headers.get("Content-Length"));
        assertNull(headers.get(CONTENT_TYPE));

        assertEquals("", out.getBody(String.class));
    }
}
