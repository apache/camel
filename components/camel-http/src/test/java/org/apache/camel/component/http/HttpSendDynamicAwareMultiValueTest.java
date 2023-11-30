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

import java.lang.reflect.Field;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpSendDynamicAwareMultiValueTest extends BaseHttpTest {

    private HttpServer localServer;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy())
                .setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/dynamicAware", new BasicValidationHandler("GET", null, null, null))
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

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:dynamicAwareWithMultiValue")
                        .toD("http://localhost:" + localServer.getLocalPort()
                             + "/dynamicAware?httpClient.responseTimeout=60000&okStatusCodeRange=200-500&foo=foo");
            }
        };
    }

    @Test
    public void testSendDynamicAwareMultiValue() throws Exception {
        Exchange e = fluentTemplate
                .to("direct:dynamicAwareWithMultiValue")
                .send();

        boolean found = context.getEndpointRegistry().containsKey("http://localhost:" + localServer.getLocalPort()
                                                                  + "?httpClient.responseTimeout=60000&okStatusCodeRange=200-500");

        assertTrue(found, "Should find static uri with multi-value");
        assertEquals("/dynamicAware", e.getIn().getHeader(Exchange.HTTP_PATH));
        assertEquals("foo=foo", e.getIn().getHeader(Exchange.HTTP_QUERY));

        HttpEndpoint httpEndpoint = (HttpEndpoint) context.getEndpoint("http://localhost:" + localServer.getLocalPort()
                                                                       + "?httpClient.responseTimeout=60000&okStatusCodeRange=200-500");

        String okStatusCodeRange = httpEndpoint.getOkStatusCodeRange();
        assertEquals("200-500", okStatusCodeRange);

        HttpClient httpClient = httpEndpoint.getHttpClient();

        Class<?> internalHttpClientClass = Class
                .forName("org.apache.hc.client5.http.impl.classic.InternalHttpClient");
        Field defaultConfig = internalHttpClientClass.getDeclaredField("defaultConfig");
        defaultConfig.setAccessible(true);
        RequestConfig config = (RequestConfig) defaultConfig.get(httpClient);
        assertEquals(60000, config.getResponseTimeout().getDuration());
    }
}
