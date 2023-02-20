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
package org.apache.camel.builder;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.model.ModelLifecycleStrategySupport;
import org.apache.camel.model.RouteDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RouteModelLifecycleTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testModelLifecycle() throws Exception {
        // add lifecycle before we add routes
        context.addModelLifecycleStrategy(new ModelLifecycleStrategySupport() {
            @Override
            public void onAddRouteDefinition(RouteDefinition route) {
                // lets mutate the template a bit
                route.getInput().setUri("direct:two");
            }
        });

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:one")
                        .to("mock:result");
            }
        });

        context.start();

        Assertions.assertEquals(1, context.getRoutes().size());

        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:two", "Hello World");
        assertMockEndpointsSatisfied();

        context.stop();
    }

}
