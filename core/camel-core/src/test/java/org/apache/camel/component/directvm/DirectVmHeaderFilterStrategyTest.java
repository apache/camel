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
package org.apache.camel.component.directvm;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.junit.Test;

/**
 *
 */
public class DirectVmHeaderFilterStrategyTest extends ContextTestSupport {

    @Test
    public void testPropertiesPropagatedOrNot() throws Exception {
        context.getRegistry().bind("headerFilterStrategy", new HeaderFilterStrategy() {
            @Override
            public boolean applyFilterToExternalHeaders(String headerName, Object headerValue, Exchange exchange) {
                return headerName.equals("Header2");
            }

            @Override
            public boolean applyFilterToCamelHeaders(String headerName, Object headerValue, Exchange exchange) {
                return headerName.equals("Header1");
            }
        });

        Exchange response = template.request("direct-vm:start.filter?headerFilterStrategy=#headerFilterStrategy&block=false", exchange -> {
            exchange.getIn().setBody("Hello World");
            exchange.getIn().setHeader("Header1", "Value1");
        });

        assertNull(response.getException());
        assertNull(response.getMessage().getHeader("Header2"));

        response = template.request("direct-vm:start.nofilter", exchange -> {
            exchange.getIn().setBody("Hello World");
            exchange.getIn().setHeader("Header1", "Value1");
        });

        assertNull(response.getException());
        assertEquals("Value2", response.getMessage().getHeader("Header2", String.class));

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct-vm:start.filter").process(exchange -> {
                    assertNull(exchange.getIn().getHeader("Header1"));
                    exchange.getIn().setHeader("Header2", "Value2");
                });

                from("direct-vm:start.nofilter").process(exchange -> {
                    assertEquals("Value1", exchange.getIn().getHeader("Header1"));
                    exchange.getIn().setHeader("Header2", "Value2");
                });
            }
        };
    }

}
