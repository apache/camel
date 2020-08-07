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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.component.http.handler.HeaderValidationHandler;
import org.apache.camel.util.URISupport;
import org.apache.commons.codec.BinaryDecoder;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolException;
import org.apache.http.auth.AUTH;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.ResponseContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class HttpProxyServerTest extends BaseHttpTest {

    private HttpServer proxy;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put("Proxy-Connection", "Keep-Alive");
        proxy = ServerBootstrap.bootstrap().
                setHttpProcessor(getBasicHttpProcessor()).
                setConnectionReuseStrategy(getConnectionReuseStrategy()).
                setResponseFactory(getHttpResponseFactory()).
                setExpectationVerifier(getHttpExpectationVerifier()).
                setSslContext(getSSLContext()).
                registerHandler("*", new HeaderValidationHandler(GET.name(), null, null, getExpectedContent(), expectedHeaders)).create();
        proxy.start();

        super.setUp();
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        if (proxy != null) {
            proxy.stop();
        }
    }

    @Override
    protected HttpProcessor getBasicHttpProcessor() {
        List<HttpRequestInterceptor> requestInterceptors = new ArrayList<>();
        requestInterceptors.add(new RequestProxyBasicAuth());
        List<HttpResponseInterceptor> responseInterceptors = new ArrayList<>();
        responseInterceptors.add(new ResponseContent());
        responseInterceptors.add(new ResponseProxyBasicUnauthorized());
        return new ImmutableHttpProcessor(requestInterceptors, responseInterceptors);
    }

    @Test
    public void testDifferentHttpProxyConfigured() throws Exception {
        HttpEndpoint http1 = context.getEndpoint("http://www.google.com?proxyAuthHost=www.myproxy.com&proxyAuthPort=1234", HttpEndpoint.class);
        HttpEndpoint http2 = context.getEndpoint("http://www.google.com?test=parameter&proxyAuthHost=www.otherproxy.com&proxyAuthPort=2345", HttpEndpoint.class);
        // HttpClientBuilder doesn't support get the configuration here

        //As the endpointUri is recreated, so the parameter could be in different place, so we use the URISupport.normalizeUri
        assertEquals("http://www.google.com?proxyAuthHost=www.myproxy.com&proxyAuthPort=1234", URISupport.normalizeUri(http1.getEndpointUri()), "Get a wrong endpoint uri of http1");
        assertEquals("http://www.google.com?proxyAuthHost=www.otherproxy.com&proxyAuthPort=2345&test=parameter", URISupport.normalizeUri(http2.getEndpointUri()), "Get a wrong endpoint uri of http2");

        assertEquals(http1.getEndpointKey(), http2.getEndpointKey(), "Should get the same EndpointKey");
    }

    @Test
    public void httpGetWithProxyAndWithoutUser() throws Exception {

        Exchange exchange = template.request("http://" + getProxyHost() + ":" + getProxyPort() + "?proxyAuthHost=" + getProxyHost() + "&proxyAuthPort=" + getProxyPort(), exchange1 -> {
        });

        assertExchange(exchange);
    }

    @Test
    public void httpGetWithProxyAndWithoutUserTwo() throws Exception {

        Exchange exchange = template.request("http://" + getProxyHost() + ":" + getProxyPort() + "?proxyHost=" + getProxyHost() + "&proxyPort=" + getProxyPort(), exchange1 -> {
        });

        assertExchange(exchange);
    }

    private String getProxyHost() {
        return proxy.getInetAddress().getHostName();
    }

    private String getProxyPort() {
        return "" + proxy.getLocalPort();
    }

    private static class RequestProxyBasicAuth implements HttpRequestInterceptor {
        @Override
        public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
            String auth = null;

            String requestLine = request.getRequestLine().toString();
            // assert we set a write GET URI
            if (requestLine.contains("http://localhost")) {
                throw new HttpException("Get a wrong proxy GET url");
            }
            Header h = request.getFirstHeader(AUTH.PROXY_AUTH_RESP);
            if (h != null) {
                String s = h.getValue();
                if (s != null) {
                    auth = s.trim();
                }
            }

            if (auth != null) {
                int i = auth.indexOf(' ');
                if (i == -1) {
                    throw new ProtocolException("Invalid Authorization header: " + auth);
                }
                String authscheme = auth.substring(0, i);
                if (authscheme.equalsIgnoreCase("basic")) {
                    String s = auth.substring(i + 1).trim();
                    byte[] credsRaw = s.getBytes("ASCII");
                    BinaryDecoder codec = new Base64();
                    try {
                        String creds = new String(codec.decode(credsRaw), "ASCII");
                        context.setAttribute("proxy-creds", creds);
                    } catch (DecoderException ex) {
                        throw new ProtocolException("Malformed BASIC credentials");
                    }
                }
            }
        }
    }

    private static class ResponseProxyBasicUnauthorized implements HttpResponseInterceptor {
        @Override
        public void process(final HttpResponse response, final HttpContext context) throws HttpException, IOException {
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED) {
                response.addHeader(AUTH.PROXY_AUTH, "Basic realm=\"test realm\"");
            }
        }
    }
}
