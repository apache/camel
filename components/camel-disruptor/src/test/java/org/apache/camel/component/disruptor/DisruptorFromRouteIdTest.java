/**
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
package org.apache.camel.component.disruptor;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 *
 */
public class DisruptorFromRouteIdTest extends CamelTestSupport {
    @Test
    public void testDisruptorFromRouteId() throws Exception {
        final MockEndpoint foo = getMockEndpoint("mock:foo");
        foo.expectedMessageCount(1);

        final MockEndpoint bar = getMockEndpoint("mock:bar");
        bar.expectedMessageCount(1);

        template.sendBody("disruptor:foo", "Hello World");

        assertMockEndpointsSatisfied();

        assertEquals("foo", foo.getReceivedExchanges().get(0).getFromRouteId());
        assertEquals("disruptor://foo", foo.getReceivedExchanges().get(0).getFromEndpoint().getEndpointUri());
        assertEquals("bar", bar.getReceivedExchanges().get(0).getFromRouteId());
        assertEquals("disruptor://bar", bar.getReceivedExchanges().get(0).getFromEndpoint().getEndpointUri());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("disruptor:foo").routeId("foo").to("mock:foo").to("disruptor:bar");

                from("disruptor:bar").routeId("bar").to("mock:bar");
            }
        };
    }
}
