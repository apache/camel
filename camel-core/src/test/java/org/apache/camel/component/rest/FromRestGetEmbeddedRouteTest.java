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
package org.apache.camel.component.rest;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.rest.RestDefinition;

public class FromRestGetEmbeddedRouteTest extends ContextTestSupport {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("dummy-test", new DummyRestConsumerFactory());
        return jndi;
    }

    protected int getExpectedNumberOfRoutes() {
        return 3;
    }

    public void testFromRestModel() throws Exception {
        assertEquals(getExpectedNumberOfRoutes(), context.getRoutes().size());

        assertEquals(2, context.getRestDefinitions().size());
        RestDefinition rest = context.getRestDefinitions().get(0);
        assertNotNull(rest);
        assertEquals("/say/hello", rest.getPath());
        assertEquals(1, rest.getVerbs().size());
        ToDefinition to = assertIsInstanceOf(ToDefinition.class, rest.getVerbs().get(0).getRoute().getOutputs().get(0));
        assertEquals("mock:hello", to.getUri());

        rest = context.getRestDefinitions().get(1);
        assertNotNull(rest);
        assertEquals("/say/bye", rest.getPath());
        assertEquals(2, rest.getVerbs().size());
        assertEquals("application/json", rest.getVerbs().get(0).getConsumes());
        to = assertIsInstanceOf(ToDefinition.class, rest.getVerbs().get(0).getRoute().getOutputs().get(0));
        assertEquals("mock:bye", to.getUri());

        // the rest becomes routes and the input is a seda endpoint created by the DummyRestConsumerFactory
        getMockEndpoint("mock:update").expectedMessageCount(1);
        template.sendBody("seda:post-say-bye", "I was here");
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
                rest("/say/hello")
                    .get()
                        .route()
                        .to("mock:hello")
                        .transform(constant("Hello World"));

                rest("/say/bye")
                    .get().consumes("application/json")
                        .route()
                        .to("mock:bye")
                        .transform(constant("Bye World"))
                        .endRest()
                    .post()
                        .to("mock:update");
            }
        };
    }
}
