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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.component.http.handler.HeaderValidationHandler;
import org.apache.camel.component.http.interceptor.RequestProxyBasicAuth;
import org.apache.camel.component.http.interceptor.ResponseProxyBasicUnauthorized;
import org.apache.camel.util.URISupport;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.ResponseContent;
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
        // Don't test anymore the Proxy-Connection header as it is highly discouraged, so its support has been removed
        // https://issues.apache.org/jira/browse/HTTPCLIENT-1957
        //        expectedHeaders.put("Proxy-Connection", "Keep-Alive");
        proxy = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("*",
                        new HeaderValidationHandler(GET.name(), null, null, getExpectedContent(), expectedHeaders))
                .create();
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
        return new DefaultHttpProcessor(requestInterceptors, responseInterceptors);
    }

    @Test
    public void testDifferentHttpProxyConfigured() throws Exception {
        HttpEndpoint http1 = context.getEndpoint("http://www.google.com?proxyAuthHost=www.myproxy.com&proxyAuthPort=1234",
                HttpEndpoint.class);
        HttpEndpoint http2 = context.getEndpoint(
                "http://www.google.com?test=parameter&proxyAuthHost=www.otherproxy.com&proxyAuthPort=2345", HttpEndpoint.class);
        // HttpClientBuilder doesn't support get the configuration here

        //As the endpointUri is recreated, so the parameter could be in different place, so we use the URISupport.normalizeUri
        assertEquals("http://www.google.com?proxyAuthHost=www.myproxy.com&proxyAuthPort=1234",
                URISupport.normalizeUri(http1.getEndpointUri()), "Get a wrong endpoint uri of http1");
        assertEquals("http://www.google.com?proxyAuthHost=www.otherproxy.com&proxyAuthPort=2345&test=parameter",
                URISupport.normalizeUri(http2.getEndpointUri()), "Get a wrong endpoint uri of http2");

        assertEquals(http1.getEndpointKey(), http2.getEndpointKey(), "Should get the same EndpointKey");
    }

    @Test
    public void httpGetWithProxyAndWithoutUser() {

        Exchange exchange = template.request("http://" + getHost() + ":" + getProxyPort() + "?proxyAuthHost="
                                             + getProxyHost() + "&proxyAuthPort=" + getProxyPort(),
                exchange1 -> {
                });

        assertExchange(exchange);
    }

    @Test
    public void httpGetWithProxyAndWithoutUserTwo() {

        Exchange exchange = template.request("http://" + getHost() + ":" + getProxyPort() + "?proxyHost=" + getProxyHost()
                                             + "&proxyPort=" + getProxyPort(),
                exchange1 -> {
                });

        assertExchange(exchange);
    }

    @Test
    public void httpGetWithProxyOnComponent() {
        HttpComponent http = context.getComponent("http", HttpComponent.class);
        http.setProxyAuthHost(getProxyHost());
        http.setProxyAuthPort(Integer.parseInt(getProxyPort()));

        Exchange exchange = template.request("http://" + getHost() + ":" + getProxyPort(), exchange1 -> {
        });

        http.setProxyAuthHost(null);
        http.setProxyAuthPort(null);

        assertExchange(exchange);
    }

    private String getHost() {
        return "127.0.0.1";
    }

    private String getProxyHost() {
        return "localhost";
    }

    private String getProxyPort() {
        return "" + proxy.getLocalPort();
    }

}
