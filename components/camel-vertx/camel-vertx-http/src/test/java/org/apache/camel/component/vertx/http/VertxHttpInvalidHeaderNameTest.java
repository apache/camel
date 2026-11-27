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

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A message header whose name is not a valid HTTP header name, such as the text of a form field without "=", is not
 * sent with the request, instead of failing the exchange.
 */
public class VertxHttpInvalidHeaderNameTest extends VertxHttpTestSupport {

    private static final String FIELD = "Save 50 percent now";

    @Test
    public void testProducerSkipsHeaderWithInvalidName() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:server");
        mock.expectedMessageCount(1);

        Exchange exchange = template.request(getProducerUri(), e -> {
            e.getIn().setBody("Hello");
            e.getIn().setHeader(FIELD, "");
            e.getIn().setHeader("X-Valid", "yes");
        });

        assertNull(exchange.getException());
        assertEquals("OK", exchange.getMessage().getBody(String.class));
        MockEndpoint.assertIsSatisfied(context);
        assertEquals("yes", mock.getExchanges().get(0).getIn().getHeader("X-Valid"));
        assertNull(mock.getExchanges().get(0).getIn().getHeader(FIELD));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getTestServerUri())
                        .to("mock:server")
                        .setBody(constant("OK"));
            }
        };
    }
}
