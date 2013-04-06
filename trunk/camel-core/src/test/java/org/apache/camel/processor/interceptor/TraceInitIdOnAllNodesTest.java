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
package org.apache.camel.processor.interceptor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.LogDefinition;
import org.apache.camel.model.OtherwiseDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.WhenDefinition;

/**
 * Tests that when tracing is enabled the ids of all the nodes is forced assigned
 * to ensure the tracer outputs node id for each node in the tracing messages.
 */
public class TraceInitIdOnAllNodesTest extends ContextTestSupport {

    public void testInitIdsOnAllNodes() throws Exception {
        getMockEndpoint("mock:camel").expectedBodiesReceived("Hello Camel");
        getMockEndpoint("mock:other").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:end").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello Camel");
        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        RouteDefinition route = context.getRouteDefinitions().get(0);
        assertNotNull(route);

        ChoiceDefinition choice = (ChoiceDefinition) route.getOutputs().get(0);
        assertEquals("choice1", choice.getId());

        WhenDefinition when = (WhenDefinition) choice.getOutputs().get(0);
        assertEquals("when1", when.getId());

        LogDefinition log1 = (LogDefinition) when.getOutputs().get(0);
        assertEquals("log1", log1.getId());

        ToDefinition to1 = (ToDefinition) when.getOutputs().get(1);
        assertEquals("camel", to1.getId());

        OtherwiseDefinition other = (OtherwiseDefinition) choice.getOutputs().get(1);
        assertEquals("otherwise1", other.getId());

        LogDefinition log2 = (LogDefinition) other.getOutputs().get(0);
        assertEquals("log2", log2.getId());

        ToDefinition to2 = (ToDefinition) other.getOutputs().get(1);
        assertEquals("to1", to2.getId());

        ToDefinition to3 = (ToDefinition) other.getOutputs().get(2);
        assertEquals("foo", to3.getId());

        ToDefinition to4 = (ToDefinition) route.getOutputs().get(1);
        assertEquals("end", to4.getId());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // when tracing is enabled, then the ids is force assigned
                context.setTracing(true);

                from("direct:start")
                    .choice()
                        .when(body().contains("Camel"))
                            .log("A Camel message")
                            .to("mock:camel").id("camel")
                        .otherwise()
                            .log("Some other kind of message")
                            .to("mock:other") // should auto generate id
                            .to("mock:foo").id("foo")
                        .end()
                    .to("mock:end").id("end");
            }
        };
    }
}
