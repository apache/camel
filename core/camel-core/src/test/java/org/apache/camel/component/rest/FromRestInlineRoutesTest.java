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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

public class FromRestInlineRoutesTest extends ContextTestSupport {

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
    public void testInlined() {
        assertEquals(getExpectedNumberOfRoutes(), context.getRoutes().size());

        assertEquals(2, context.getRestDefinitions().size());
        assertEquals(2, context.getRouteDefinitions().size());

        // the rest becomes routes and the input is a seda endpoint created by
        // the DummyRestConsumerFactory
        String out = template.requestBody("seda:get-say-hello", "Me", String.class);
        assertEquals("Hello World", out);
        String out2 = template.requestBody("seda:get-say-bye", "Me", String.class);
        assertEquals("Bye World", out2);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().host("localhost").inlineRoutes(true);

                rest("/say/hello").get().to("direct:hello");
                rest("/say/bye").get().to("direct:bye");

                from("direct:hello").transform().constant("Hello World");
                from("direct:bye").transform().constant("Bye World");
            }
        };
    }
}
