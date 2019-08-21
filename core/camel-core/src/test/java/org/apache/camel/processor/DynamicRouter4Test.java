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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.ExchangeHelper;
import org.junit.Test;

public class DynamicRouter4Test extends ContextTestSupport {
    @Test
    public void testDynamicRouter() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);
        getMockEndpoint("mock:c").expectedMessageCount(1);

        template.sendBody("direct:start-1", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start-1").dynamicRouter().exchange(DynamicRouter4Test::slip);
            }
        };
    }

    public static String slip(Exchange exchange) {
        String previous = ExchangeHelper.getHeaderOrProperty(exchange, Exchange.SLIP_ENDPOINT, String.class);
        if (previous == null) {
            return "mock:a,mock:b";
        } else if ("mock://b".equals(previous)) {
            return "mock:c";
        }

        // no more so return null
        return null;
    }
}
