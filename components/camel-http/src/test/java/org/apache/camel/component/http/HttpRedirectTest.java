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

import java.io.IOException;

import org.apache.camel.Exchange;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.GET;
import static org.apache.camel.component.http.HttpMethods.POST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 *
 */
public class HttpRedirectTest extends BaseHttpTest {

    private HttpServer localServer;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/someplaceelse", new BasicValidationHandler(GET.name(), null, null, "Bye World"))
                .register("/redirectplace", new BasicValidationHandler(POST.name(), null, null, ""))
                .register("/testPost", new RedirectPostHandler(308))
                .register("/test", new RedirectHandler(HttpStatus.SC_MOVED_PERMANENTLY)).create();
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
    public void httpRedirectFalse() throws Exception {

        String uri = "http://localhost:" + localServer.getLocalPort()
                     + "/test?httpClient.redirectsEnabled=false&httpClient.responseTimeout=60000&httpClient.connectTimeout=60000"
                     + "&httpConnection.validateAfterInactivity=-1";
        Exchange out = template.request(uri, exchange -> {
            // no data
        });

        assertNotNull(out);
        HttpOperationFailedException cause = out.getException(HttpOperationFailedException.class);
        assertNotNull(cause);
        assertEquals(HttpStatus.SC_MOVED_PERMANENTLY, cause.getStatusCode());
        assertEquals(
                "http://localhost:" + localServer.getLocalPort() + "/someplaceelse",
                cause.getRedirectLocation());
    }

    @Test
    public void httpHandleRedirect() throws Exception {

        String uri = "http://localhost:" + localServer.getLocalPort()
                     + "/test?httpClient.responseTimeout=60000&httpClient.connectTimeout=60000"
                     + "&httpConnection.validateAfterInactivity=-1&followRedirects=true";
        Exchange out = template.request(uri, exchange -> {
            // no data
        });

        assertNotNull(out);
        assertEquals(HttpStatus.SC_OK, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("OK", out.getMessage().getHeader(Exchange.HTTP_RESPONSE_TEXT));
        assertEquals("Bye World", out.getMessage().getBody(String.class));
    }

    @Test
    public void httpHandleFollowRedirect() throws Exception {

        String uri = "http://localhost:" + localServer.getLocalPort()
                     + "/testPost?httpClient.responseTimeout=60000&httpClient.connectTimeout=60000"
                     + "&httpConnection.validateAfterInactivity=-1&followRedirects=true&httpMethod=POST";
        Exchange out = template.request(uri, exchange -> {
            // no data
        });

        assertNotNull(out);
        assertEquals(HttpStatus.SC_OK, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("OK", out.getMessage().getHeader(Exchange.HTTP_RESPONSE_TEXT));
        assertEquals("", out.getMessage().getBody(String.class));
    }

    @Test
    public void httpHandleFollowRedirectWithComponent() throws Exception {

        HttpComponent component = context.getComponent("http", HttpComponent.class);
        component.setFollowRedirects(true);
        component.setConnectionTimeToLive(1000L);
        String uri = "http://localhost:" + localServer.getLocalPort()
                     + "/testPost?httpClient.responseTimeout=60000&httpClient.connectTimeout=60000"
                     + "&httpConnection.validateAfterInactivity=-1&httpMethod=POST";
        HttpEndpoint httpEndpoint = (HttpEndpoint) component.createEndpoint(uri);

        Exchange out = template.request(httpEndpoint, exchange -> {
            // no data
        });

        assertNotNull(out);
        assertEquals(HttpStatus.SC_OK, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("OK", out.getMessage().getHeader(Exchange.HTTP_RESPONSE_TEXT));
        assertEquals("", out.getMessage().getBody(String.class));

    }

    private final class RedirectHandler implements HttpRequestHandler {

        private final int code;

        private RedirectHandler(int code) {
            this.code = code;
        }

        @Override
        public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
                throws HttpException, IOException {
            response.setHeader("location", "http://localhost:"
                                           + localServer.getLocalPort() + "/someplaceelse");
            response.setCode(code);
        }
    }

    private final class RedirectPostHandler implements HttpRequestHandler {

        private final int code;

        private RedirectPostHandler(int code) {
            this.code = code;
        }

        @Override
        public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
                throws HttpException, IOException {
            response.setHeader("location", "http://localhost:"
                                           + localServer.getLocalPort() + "/redirectplace");
            response.setCode(code);
        }
    }

}
