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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.http4.handler.BasicValidationHandler;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
public class HttpRedirectTest extends BaseHttpTest {

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
                registerHandler("/someplaceelse", new BasicValidationHandler("GET", null, null, "Bye World")).
                registerHandler("/test", new RedirectHandler(HttpStatus.SC_MOVED_PERMANENTLY)).
                create();
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
    public void httpRedirect() throws Exception {

        String uri = "http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort()
                + "/test?httpClient.redirectsEnabled=false&httpClient.socketTimeout=60000&httpClient.connectTimeout=60000"
                + "&httpClient.staleConnectionCheckEnabled=false";
        Exchange out = template.request(uri, new Processor() {
            public void process(Exchange exchange) throws Exception {
                // no data
            }
        });

        assertNotNull(out);
        HttpOperationFailedException cause = out.getException(HttpOperationFailedException.class);
        assertNotNull(cause);
        assertEquals(HttpStatus.SC_MOVED_PERMANENTLY, cause.getStatusCode());
        assertEquals("http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "/someplaceelse", cause.getRedirectLocation());
    }

    @Test
    public void httpHandleRedirect() throws Exception {

        String uri = "http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort()
                + "/test?httpClient.socketTimeout=60000&httpClient.connectTimeout=60000"
                + "&httpClient.staleConnectionCheckEnabled=false";
        Exchange out = template.request(uri, new Processor() {
            public void process(Exchange exchange) throws Exception {
                // no data
            }
        });

        assertNotNull(out);
        assertEquals(HttpStatus.SC_OK, out.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("OK", out.getOut().getHeader(Exchange.HTTP_RESPONSE_TEXT));
        assertEquals("Bye World", out.getOut().getBody(String.class));
    }

    private final class RedirectHandler implements HttpRequestHandler {

        private final int code;

        private RedirectHandler(int code) {
            this.code = code;
        }

        public void handle(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws HttpException, IOException {
            httpResponse.setHeader("location", "http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "/someplaceelse");
            httpResponse.setStatusCode(code);
        }
    }

}
