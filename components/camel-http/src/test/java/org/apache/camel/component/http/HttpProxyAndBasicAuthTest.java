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

import org.apache.camel.Exchange;
import org.apache.camel.component.http.handler.ProxyAndBasicAuthenticationValidationHandler;
import org.apache.camel.component.http.interceptor.RequestBasicAuth;
import org.apache.camel.component.http.interceptor.RequestProxyBasicAuth;
import org.apache.camel.component.http.interceptor.ResponseBasicUnauthorized;
import org.apache.camel.component.http.interceptor.ResponseProxyBasicUnauthorized;
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

public class HttpProxyAndBasicAuthTest extends BaseHttpTest {

    private HttpServer proxy;

    private final String user = "camel";
    private final String password = "password";
    private final String proxyUser = "proxyuser";
    private final String proxyPassword = "proxypassword";

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        proxy = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .registerVirtual("authtest.org", "*", new ProxyAndBasicAuthenticationValidationHandler(
                        GET.name(),
                        null, null, getExpectedContent(), user, password, proxyUser, proxyPassword))
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
        requestInterceptors.add(new RequestBasicAuth());
        List<HttpResponseInterceptor> responseInterceptors = new ArrayList<>();
        responseInterceptors.add(new ResponseContent());
        responseInterceptors.add(new ResponseProxyBasicUnauthorized());
        responseInterceptors.add(new ResponseBasicUnauthorized());
        return new DefaultHttpProcessor(requestInterceptors, responseInterceptors);
    }

    @Test
    public void httpGetWithProxyAndUser() {
        Exchange exchange = template.request("http://authtest.org" + "?proxyAuthHost=localhost"
                                             + "&proxyAuthPort=" + proxy.getLocalPort()
                                             + "&proxyAuthUsername=" + proxyUser + "&proxyAuthPassword=" + proxyPassword
                                             + "&authUsername=" + user + "&authPassword=" + password,
                exchange1 -> {
                });

        assertExchange(exchange);
    }
}
