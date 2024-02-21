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
import org.apache.camel.model.RouteTemplateDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RouteTemplateModelLifecycleTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testModelLifecycle() throws Exception {
        // add lifecycle before we add routes
        context.addModelLifecycleStrategy(new ModelLifecycleStrategySupport() {
            @Override
            public void onAddRouteTemplateDefinition(RouteTemplateDefinition template) {
                // lets mutate the template a bit
                template.getRoute().getInput().setUri("seda:{{foo}}");
            }
        });

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .from("direct:{{foo}}")
                        .to("mock:{{bar}}");
            }
        });

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "one")
                .parameter("bar", "result")
                .routeId("myRoute")
                .add();

        context.start();

        Assertions.assertEquals(1, context.getRoutes().size());

        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("seda:one", "Hello World");
        assertMockEndpointsSatisfied();

        context.stop();
    }

    @Test
    public void testModelLifecycleViaHandler() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .from("direct:{{foo}}")
                        .to("mock:{{bar}}");
            }
        });

        context.start();

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "one")
                .parameter("bar", "result")
                .routeId("myRoute")
                .handler(template -> {
                    // lets mutate the template a bit
                    // (notice changes are global as its not a clone of the template - not possible)
                    template.getRoute().getInput().setUri("seda:{{foo}}");
                })
                .add();

        Assertions.assertEquals(1, context.getRoutes().size());

        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("seda:one", "Hello World");
        assertMockEndpointsSatisfied();

        context.stop();
    }

}
