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

import java.io.IOException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class RouteIdTest extends ContextTestSupport {

    @Test
    public void testRouteId() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        String id = context.getRouteDefinitions().get(0).getId();
        assertEquals("myCoolRoute", id);
    }

    @Test
    public void testRouteIdFailed() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:start", "Kabom");

        assertMockEndpointsSatisfied();

        String id = context.getRouteDefinitions().get(0).getId();
        assertEquals("myCoolRoute", id);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).handled(true).to("mock:error").end();

                from("direct:start").onException(IOException.class).redeliveryDelay(0).maximumRedeliveries(5).end().routeId("myCoolRoute").choice().when(body().contains("Kabom"))
                    .throwException(new IllegalArgumentException("Damn")).otherwise().to("mock:result").end();
            }
        };
    }
}
