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
package org.apache.camel.component.rest;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FromRestGetInterceptFromTest extends ContextTestSupport {

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry jndi = super.createCamelRegistry();
        jndi.bind("dummy-rest", new DummyRestConsumerFactory());
        return jndi;
    }

    @Test
    public void testFromRestModel() throws Exception {
        getMockEndpoint("mock:hello").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:intercept").expectedMessageCount(1);

        String out = template.requestBody("seda:get-say-hello", "I was here", String.class);
        assertEquals("Bye World", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().host("localhost").inlineRoutes(true);
                interceptFrom("rest:*").to("mock:intercept");

                rest("/say/hello").get().to("direct:hello");

                from("direct:hello")
                        .to("mock:hello").to("mock:bar").transform().constant("Bye World");
            }
        };
    }
}
