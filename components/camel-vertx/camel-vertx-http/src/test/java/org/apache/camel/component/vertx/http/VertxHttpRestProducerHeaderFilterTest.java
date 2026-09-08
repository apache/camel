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
package org.apache.camel.component.vertx.http;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Message;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VertxHttpRestProducerHeaderFilterTest extends VertxHttpTestSupport {

    @Test
    public void testRestProducerAppliesTheOutboundFilter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:input");
        mock.expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put("id", "123");
        headers.put("Via", "1.1 rogue-proxy");
        headers.put("Cache-Control", "no-cache");
        headers.put("X-Custom", "custom-value");

        String out = template.requestBodyAndHeaders("direct:start", null, headers, String.class);
        assertThat(out).isEqualTo("Hello World");

        MockEndpoint.assertIsSatisfied(context);

        Message received = mock.getReceivedExchanges().get(0).getMessage();
        // excluded on the outbound direction by the common HTTP filter set, so they must not reach the wire
        assertThat(received.getHeader("Via")).isNull();
        assertThat(received.getHeader("Cache-Control")).isNull();
        // already consumed by the uri template, so it must not be sent as an HTTP header as well
        assertThat(received.getHeader("id")).isNull();
        // not filtered on either direction
        assertThat(received.getHeader("X-Custom")).isEqualTo("custom-value");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration()
                        .producerComponent("vertx-http")
                        .host("localhost").port(getPort());

                from("direct:start")
                        .to("rest:get:foo/{id}");

                from(getTestServerUri() + "/foo/123")
                        .to("mock:input")
                        .setBody(constant("Hello World"));
            }
        };
    }
}
