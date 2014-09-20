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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.http4.handler.HeaderValidationHandler;
import org.apache.camel.component.http4.handler.ProxyAuthenticationValidationHandler;
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
import org.apache.http.localserver.LocalTestServer;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.ResponseContent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @version 
 */
public class HttpProxyServerTest extends BaseHttpTest {

    private LocalTestServer proxy;
    private String user = "camel";
    private String password = "password";

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        
        List<HttpRequestInterceptor> requestInterceptors = new ArrayList<HttpRequestInterceptor>();
        requestInterceptors.add(new RequestProxyBasicAuth());
        List<HttpResponseInterceptor> responseInterceptors = new ArrayList<HttpResponseInterceptor>();
        responseInterceptors.add(new ResponseContent());
        responseInterceptors.add(new ResponseProxyBasicUnauthorized());
        ImmutableHttpProcessor httpproc = new ImmutableHttpProcessor(requestInterceptors, responseInterceptors);

        proxy = new LocalTestServer(httpproc, null);
        proxy.start();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        if (proxy != null) {
            proxy.stop();
        }

        super.tearDown();
    }
    
    @Test
    public void testDifferentHttpProxyConfigured() throws Exception {
        HttpEndpoint http1 = context.getEndpoint("http4://www.google.com?proxyAuthHost=myproxy&proxyAuthPort=1234", HttpEndpoint.class);
        HttpEndpoint http2 = context.getEndpoint("http4://www.google.com?test=parameter&proxyAuthHost=myotherproxy&proxyAuthPort=2345", HttpEndpoint.class);
        // HttpClientBuilder doesn't support get the configuration here
        
        //As the endpointUri is recreated, so the parameter could be in different place, so we use the URISupport.normalizeUri
        assertEquals("Get a wrong endpoint uri of http1", "http4://www.google.com?proxyAuthHost=myproxy&proxyAuthPort=1234", URISupport.normalizeUri(http1.getEndpointUri()));
        assertEquals("Get a wrong endpoint uri of http2", "http4://www.google.com?proxyAuthHost=myotherproxy&proxyAuthPort=2345&test=parameter", URISupport.normalizeUri(http2.getEndpointUri()));

        assertEquals("Should get the same EndpointKey", http1.getEndpointKey(), http2.getEndpointKey());
    }
    

    @Test
    public void httpGetWithProxyAndWithoutUser() throws Exception {
        Map<String, String> expectedHeaders = new HashMap<String, String>();
        expectedHeaders.put("Host", getHostName() + ":" + getPort());
        expectedHeaders.put("Proxy-Connection", "Keep-Alive");
        proxy.register("*", new HeaderValidationHandler("GET", null, null, getExpectedContent(), expectedHeaders));

        Exchange exchange = template.request("http4://" + getHostName() + ":" + getPort() + "?proxyAuthHost=" + getProxyHost() + "&proxyAuthPort=" + getProxyPort(), new Processor() {
            public void process(Exchange exchange) throws Exception {
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void httpGetWithProxyInCamelContextAndWithoutUser() throws Exception {
        context.getProperties().put("http.proxyHost", getProxyHost());
        context.getProperties().put("http.proxyPort", String.valueOf(getProxyPort()));

        Map<String, String> expectedHeaders = new HashMap<String, String>();
        expectedHeaders.put("Host", getHostName() + ":" + getPort());
        expectedHeaders.put("Proxy-Connection", "Keep-Alive");

        try {
            proxy.register("*", new HeaderValidationHandler("GET", null, null, getExpectedContent(), expectedHeaders));

            Exchange exchange = template.request("http4://" + getHostName() + ":" + getPort(), new Processor() {
                public void process(Exchange exchange) throws Exception {
                }
            });

            assertExchange(exchange);
        } finally {
            context.getProperties().remove("http.proxyHost");
            context.getProperties().remove("http.proxyPort");
        }
    }

    @Test
    public void httpGetWithDuplicateProxyConfigurationAndWithoutUser() throws Exception {
        context.getProperties().put("http.proxyHost", "XXX");
        context.getProperties().put("http.proxyPort", "11111");

        Map<String, String> expectedHeaders = new HashMap<String, String>();
        expectedHeaders.put("Host", getHostName() + ":" + getPort());
        expectedHeaders.put("Proxy-Connection", "Keep-Alive");

        try {
            proxy.register("*", new HeaderValidationHandler("GET", null, null, getExpectedContent(), expectedHeaders));

            Exchange exchange = template.request("http4://" + getHostName() + ":" + getPort() + "?proxyAuthHost="
                    + getProxyHost() + "&proxyAuthPort=" + getProxyPort(), new Processor() {
                        public void process(Exchange exchange) throws Exception {
                        }
                    });

            assertExchange(exchange);
        } finally {
            context.getProperties().remove("http.proxyHost");
            context.getProperties().remove("http.proxyPort");
        }
    }

    @Test
    public void httpGetWithProxyAndWithUser() throws Exception {
        proxy.register("*", new ProxyAuthenticationValidationHandler("GET", null, null, getExpectedContent(), user, password));

        Exchange exchange = template.request("http4://" + getHostName() + ":" + getPort() + "?proxyAuthHost="
                + getProxyHost() + "&proxyAuthPort=" + getProxyPort() + "&proxyAuthUsername=camel&proxyAuthPassword=password", new Processor() {
                    public void process(Exchange exchange) throws Exception {
                    }
                });

        assertExchange(exchange);
    }
    
    public void httpGetPullEndpointWithProxyAndWithUser() {
        proxy.register("*", new ProxyAuthenticationValidationHandler("GET", null, null, getExpectedContent(), user, password));

        Exchange exchange = consumer.receive("http4://" + getHostName() + ":" + getPort() + "?proxyAuthHost="
                + getProxyHost() + "&proxyAuthPort=" + getProxyPort() + "&proxyAuthUsername=camel&proxyAuthPassword=password");

        assertExchange(exchange);
    }

    private String getProxyHost() {
        return proxy.getServiceAddress().getHostName();
    }

    private int getProxyPort() {
        return proxy.getServiceAddress().getPort();
    }
    
    class RequestProxyBasicAuth implements HttpRequestInterceptor {
        public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
            String auth = null;
            
            String requestLine = request.getRequestLine().toString();
            // assert we set a write GET URI
            if (requestLine.contains("http4://localhost")) {
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

    class ResponseProxyBasicUnauthorized implements HttpResponseInterceptor {
        public void process(final HttpResponse response, final HttpContext context) throws HttpException, IOException {
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED) {
                response.addHeader(AUTH.PROXY_AUTH, "Basic realm=\"test realm\"");
            }
        }
    }
}