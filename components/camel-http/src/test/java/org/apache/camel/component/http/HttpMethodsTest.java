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

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.DELETE;
import static org.apache.camel.component.http.HttpMethods.GET;
import static org.apache.camel.component.http.HttpMethods.HEAD;
import static org.apache.camel.component.http.HttpMethods.OPTIONS;
import static org.apache.camel.component.http.HttpMethods.PATCH;
import static org.apache.camel.component.http.HttpMethods.POST;
import static org.apache.camel.component.http.HttpMethods.PUT;
import static org.apache.camel.component.http.HttpMethods.TRACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpMethodsTest extends BaseHttpTest {

    private HttpServer localServer;

    private String baseUrl;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/get", new BasicValidationHandler(GET.name(), null, null, getExpectedContent()))
                .register("/patch", new BasicValidationHandler(PATCH.name(), null, null, getExpectedContent()))
                .register("/patch1",
                        new BasicValidationHandler(PATCH.name(), null, "rocks camel?", getExpectedContent()))
                .register("/post", new BasicValidationHandler(POST.name(), null, null, getExpectedContent()))
                .register("/post1", new BasicValidationHandler(POST.name(), null, "rocks camel?", getExpectedContent()))
                .register("/put", new BasicValidationHandler(PUT.name(), null, null, getExpectedContent()))
                .register("/trace", new BasicValidationHandler(TRACE.name(), null, null, getExpectedContent()))
                .register("/options", new BasicValidationHandler(OPTIONS.name(), null, null, getExpectedContent()))
                .register("/delete", new BasicValidationHandler(DELETE.name(), null, null, getExpectedContent()))
                .register("/delete1", new BasicValidationHandler(DELETE.name(), null, null, getExpectedContent()))
                .register("/head", new BasicValidationHandler(HEAD.name(), null, null, getExpectedContent())).create();
        localServer.start();

        baseUrl = "http://localhost:" + localServer.getLocalPort();

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
    public void httpGet() throws Exception {

        Exchange exchange = template.request(baseUrl + "/get", exchange1 -> {
        });

        assertExchange(exchange);
    }

    @Test
    public void httpGetWithUriParam() throws Exception {

        Exchange exchange = template.request(baseUrl + "/get?httpMethod=GET",
                exchange1 -> exchange1.getIn().setHeader(Exchange.HTTP_METHOD, "POST"));

        assertExchange(exchange);
    }

    @Test
    public void httpPatch() throws Exception {

        Exchange exchange = template.request(baseUrl + "/patch?throwExceptionOnFailure=false",
                exchange1 -> exchange1.getIn().setHeader(Exchange.HTTP_METHOD, "PATCH"));

        assertNotNull(exchange);
        assertTrue(exchange.hasOut());

        Message out = exchange.getMessage();
        Map<String, Object> headers = out.getHeaders();
        assertEquals(HttpStatus.SC_OK, headers.get(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("OK", headers.get(Exchange.HTTP_RESPONSE_TEXT));
        assertEquals("12", headers.get("Content-Length"));
        assertNotNull(headers.get("Content-Type"), "Should have Content-Type header");
        assertEquals("camel rocks!", out.getBody(String.class));
    }

    @Test
    public void httpPatchWithBody() throws Exception {

        Exchange exchange = template.request(baseUrl + "/patch1?throwExceptionOnFailure=false",
                exchange1 -> exchange1.getIn().setBody("rocks camel?"));

        assertNotNull(exchange);
        assertTrue(exchange.hasOut());

        Message out = exchange.getMessage();
        Map<String, Object> headers = out.getHeaders();
        assertEquals(HttpStatus.SC_METHOD_FAILURE, headers.get(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("0", headers.get("Content-Length"));
        assertEquals("", out.getBody(String.class));
    }

    @Test
    public void httpPost() throws Exception {

        Exchange exchange
                = template.request(baseUrl + "/post", exchange1 -> exchange1.getIn().setHeader(Exchange.HTTP_METHOD, "POST"));

        assertExchange(exchange);
    }

    @Test
    public void httpPostWithBody() throws Exception {

        Exchange exchange = template.request(baseUrl + "/post1", exchange1 -> exchange1.getIn().setBody("rocks camel?"));

        assertExchange(exchange);
    }

    @Test
    public void httpPut() throws Exception {

        Exchange exchange
                = template.request(baseUrl + "/put", exchange1 -> exchange1.getIn().setHeader(Exchange.HTTP_METHOD, "PUT"));

        assertExchange(exchange);
    }

    @Test
    public void httpTrace() throws Exception {

        Exchange exchange
                = template.request(baseUrl + "/trace", exchange1 -> exchange1.getIn().setHeader(Exchange.HTTP_METHOD, "TRACE"));

        assertExchange(exchange);
    }

    @Test
    public void httpOptions() throws Exception {

        Exchange exchange = template.request(baseUrl + "/options",
                exchange1 -> exchange1.getIn().setHeader(Exchange.HTTP_METHOD, "OPTIONS"));

        assertExchange(exchange);
    }

    @Test
    public void httpDelete() throws Exception {

        Exchange exchange = template.request(baseUrl + "/delete",
                exchange1 -> exchange1.getIn().setHeader(Exchange.HTTP_METHOD, "DELETE"));

        assertExchange(exchange);
    }

    @Test
    public void httpDeleteWithBody() throws Exception {

        Exchange exchange = template.request(baseUrl + "/delete1?deleteWithBody=true", exchange1 -> {
            exchange1.getIn().setHeader(Exchange.HTTP_METHOD, "DELETE");
            exchange1.getIn().setBody("rocks camel?");
        });

        assertExchange(exchange);

        // the http server will not provide body on HTTP DELETE so we cannot test the server side
    }

    @Test
    public void httpGetWithBody() throws Exception {

        Exchange exchange = template.request(baseUrl + "/get?getWithBody=true", exchange1 -> {
            exchange1.getIn().setHeader(Exchange.HTTP_METHOD, "GET");
            exchange1.getIn().setBody("rocks camel?");
        });

        assertExchange(exchange);

        // the http server will not provide body on HTTP GET so we cannot test the server side
    }

    @Test
    public void httpHead() throws Exception {

        Exchange exchange
                = template.request(baseUrl + "/head", exchange1 -> exchange1.getIn().setHeader(Exchange.HTTP_METHOD, "HEAD"));

        assertNotNull(exchange);

        Message out = exchange.getMessage();
        assertNotNull(out);
        assertHeaders(out.getHeaders());
        assertNull(out.getBody(String.class));
    }

}
