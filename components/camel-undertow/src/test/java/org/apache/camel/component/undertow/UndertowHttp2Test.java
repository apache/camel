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
package org.apache.camel.component.undertow;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.transport.HttpClientTransportOverHTTP2;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class UndertowHttp2Test extends BaseUndertowTest {
    private static final Logger LOG = LoggerFactory.getLogger(UndertowHttp2Test.class);

    private static final String RESPONSE = "Http2 Greetings";

    @Test
    public void testHttp2Protocol() throws Exception {
        final HTTP2Client http2Client = new HTTP2Client();
        final HttpClient httpClient = new HttpClient(new HttpClientTransportOverHTTP2(http2Client));
        httpClient.start();

        try {
            final ContentResponse resp = httpClient.GET("http://localhost:" + getPort() + "/myapp");

            assertEquals(200, resp.getStatus());
            assertEquals(HttpVersion.HTTP_2, resp.getVersion());
            assertEquals(RESPONSE, new String(resp.getContent()));
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
            fail("HTTP2 endpoint not exposed!, maybe it's not supported?");
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        UndertowComponent component = context.getComponent("undertow", UndertowComponent.class);

        UndertowHostOptions undertowHostOptions = new UndertowHostOptions();
        undertowHostOptions.setHttp2Enabled(true);

        component.setHostOptions(undertowHostOptions);

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("undertow:http://localhost:{{port}}/myapp")
                        .setBody().constant(RESPONSE);
            }
        };
    }
}
