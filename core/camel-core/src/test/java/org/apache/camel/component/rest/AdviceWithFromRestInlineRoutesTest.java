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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.TransformDefinition;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

public class AdviceWithFromRestInlineRoutesTest extends ContextTestSupport {

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry jndi = super.createCamelRegistry();
        jndi.bind("dummy-rest", new DummyRestConsumerFactory());
        return jndi;
    }

    protected int getExpectedNumberOfRoutes() {
        return 2; // inlined routes so there are only 2
    }

    @Test
    public void testAdviceWithInlined() throws Exception {
        AdviceWith.adviceWith(context.getRouteDefinition("hello"), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveAddLast().to("mock:hello");
            }
        });
        AdviceWith.adviceWith(context.getRouteDefinition("bye"), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveByType(TransformDefinition.class).replace().transform().simple("Echo ${body}");
                weaveAddLast().to("mock:bye");
            }
        });

        getMockEndpoint("mock:hello").expectedMessageCount(1);
        getMockEndpoint("mock:bye").expectedMessageCount(1);

        assertEquals(getExpectedNumberOfRoutes(), context.getRoutes().size());

        assertEquals(2, context.getRestDefinitions().size());
        assertEquals(2, context.getRouteDefinitions().size());

        // the rest becomes routes and the input is a seda endpoint created by
        // the DummyRestConsumerFactory
        String out = template.requestBody("seda:get-say-hello", "Me", String.class);
        assertEquals("Hello World", out);
        String out2 = template.requestBody("seda:get-say-bye", "You", String.class);
        assertEquals("Echo You", out2);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().host("localhost").inlineRoutes(true);

                rest("/say/hello").get().to("direct:hello");
                rest("/say/bye").get().to("direct:bye");

                from("direct:hello").routeId("hello").transform().constant("Hello World");
                from("direct:bye").routeId("bye").transform().constant("Bye World");
            }
        };
    }
}
