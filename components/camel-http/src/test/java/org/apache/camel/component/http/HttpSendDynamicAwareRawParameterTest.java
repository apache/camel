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
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HttpSendDynamicAwareRawParameterTest extends BaseHttpTest {

    private HttpServer localServer;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy())
                .setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/dynamicAware", new BasicValidationHandler("GET", "par1=val1&par2=val2", null, null))
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
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:dynamicAwareWithRaw")
                        .toD("http://localhost:" + localServer.getLocalPort()
                             + "/dynamicAware?par1=RAW(${headers.par1})&par2=RAW{${headers.par2}}");
            }
        };
    }

    @Test
    public void testDynamicAwareHeadersQuery() throws Exception {
        Exchange e = fluentTemplate
                .to("direct:dynamicAwareWithRaw")
                .withHeader("par1", "val1")
                .withHeader("par2", "val2")
                .send();

        assertEquals("/dynamicAware", e.getIn().getHeader(Exchange.HTTP_PATH));
        assertEquals("par1=val1&par2=val2", e.getIn().getHeader(Exchange.HTTP_QUERY));
    }
}
