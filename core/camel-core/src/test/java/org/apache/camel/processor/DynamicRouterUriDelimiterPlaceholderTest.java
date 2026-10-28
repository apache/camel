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
package org.apache.camel.processor;

import java.util.Properties;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperty;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

class DynamicRouterUriDelimiterPlaceholderTest extends ContextTestSupport {

    @Test
    void uriDelimiterResolvesPropertyPlaceholder() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello World");
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        Properties properties = new Properties();
        properties.setProperty("slip.delimiter", ";");
        context.getPropertiesComponent().setInitialProperties(properties);
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").dynamicRouter(method(DynamicRouterUriDelimiterPlaceholderTest.class, "slip"))
                        .uriDelimiter("{{slip.delimiter}}");
            }
        };
    }

    /**
     * The slip of the dynamic router: two endpoints separated by the configured delimiter, then the end.
     */
    public static String slip(@ExchangeProperty(Exchange.SLIP_ENDPOINT) String previous) {
        return previous == null ? "mock:a;mock:b" : null;
    }
}
