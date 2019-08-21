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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.junit.Test;

public class FromRestUriPrefixTest extends FromRestGetTest {

    @Override
    @Test
    public void testFromRestModel() throws Exception {
        assertEquals(getExpectedNumberOfRoutes(), context.getRoutes().size());

        assertEquals(1, context.getRestDefinitions().size());
        RestDefinition rest = context.getRestDefinitions().get(0);
        assertNotNull(rest);
        assertEquals("/say/", rest.getPath());
        assertEquals(3, rest.getVerbs().size());
        assertEquals("/hello", rest.getVerbs().get(0).getUri());
        assertEquals("/bye", rest.getVerbs().get(1).getUri());
        assertEquals("/hi", rest.getVerbs().get(2).getUri());
        ToDefinition to = assertIsInstanceOf(ToDefinition.class, rest.getVerbs().get(0).getTo());
        assertEquals("direct:hello", to.getUri());
        to = assertIsInstanceOf(ToDefinition.class, rest.getVerbs().get(1).getTo());
        assertEquals("direct:bye", to.getUri());

        // the rest becomes routes and the input is a seda endpoint created by
        // the DummyRestConsumerFactory
        getMockEndpoint("mock:update").expectedMessageCount(1);
        template.sendBody("seda:post-say-hi", "I was here");
        assertMockEndpointsSatisfied();

        String out = template.requestBody("seda:get-say-hello", "Me", String.class);
        assertEquals("Hello World", out);
        String out2 = template.requestBody("seda:get-say-bye", "Me", String.class);
        assertEquals("Bye World", out2);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().host("localhost");
                // we have logic to cleanup those paths so there is only one /
                // between the paths
                rest("/say/").get("/hello").to("direct:hello").get("/bye").consumes("application/json").to("direct:bye").post("/hi").to("mock:update");

                from("direct:hello").transform().constant("Hello World");

                from("direct:bye").transform().constant("Bye World");
            }
        };
    }
}
