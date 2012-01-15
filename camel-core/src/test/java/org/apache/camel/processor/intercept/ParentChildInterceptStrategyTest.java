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
package org.apache.camel.processor.intercept;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.InterceptStrategy;

/**
 *
 */
public class ParentChildInterceptStrategyTest extends ContextTestSupport {

    protected static final List<String> LIST = new ArrayList<String>();

    public void testParentChild() throws Exception {
        getMockEndpoint("mock:done").expectedMessageCount(1);
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);
        getMockEndpoint("mock:c").expectedMessageCount(1);
        getMockEndpoint("mock:d").expectedMessageCount(0);
        getMockEndpoint("mock:e").expectedMessageCount(0);

        template.sendBody("direct:start", "Hello Camel");

        assertMockEndpointsSatisfied();

        assertEquals(7, LIST.size());
        assertEquals("Parent route -> target task-a", LIST.get(0));
        assertEquals("Parent when -> target task-b", LIST.get(1));
        assertEquals("Parent when -> target task-c", LIST.get(2));
        assertEquals("Parent when2 -> target task-d", LIST.get(3));
        assertEquals("Parent otherwise -> target task-e", LIST.get(4));
        assertEquals("Parent route -> target choice", LIST.get(5));
        // the last one has no custom id so its using its label instead
        assertEquals("Parent route -> target mock:done", LIST.get(6));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addInterceptStrategy(new MyParentChildInterceptStrategy());

                from("direct:start").routeId("route")
                    .to("mock:a").id("task-a")
                    .choice().id("choice")
                        .when(simple("${body} contains 'Camel'")).id("when")
                            .to("mock:b").id("task-b")
                            .to("mock:c").id("task-c")
                        .when(simple("${body} contains 'Donkey'")).id("when2")
                            .to("mock:d").id("task-d")
                        .otherwise().id("otherwise")
                            .to("mock:e").id("task-e")
                    .end()
                    .to("mock:done");
            }
        };
    }

    public static final class MyParentChildInterceptStrategy implements InterceptStrategy {

        @Override
        public Processor wrapProcessorInInterceptors(final CamelContext context, final ProcessorDefinition<?> definition,
                                                     final Processor target, final Processor nextTarget) throws Exception {
            String targetId = definition.hasCustomIdAssigned() ? definition.getId() : definition.getLabel();
            ProcessorDefinition<?> parent = definition.getParent();
            String parentId = "";
            if (parent != null) {
                parentId = parent.hasCustomIdAssigned() ? parent.getId() : parent.getLabel();
            }

            LIST.add("Parent " + parentId + " -> target " + targetId);

            return target;
        }

    }

}
