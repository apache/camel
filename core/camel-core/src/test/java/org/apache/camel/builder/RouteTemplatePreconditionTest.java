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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The test ensuring that the precondition set on a rule determines if the route is included or not
 */
class RouteTemplatePreconditionTest extends ContextTestSupport {

    @Test
    void testRouteIncluded() throws Exception {
        TemplatedRouteBuilder.builder(context, "myTemplateWithPrecondition")
                .parameter("protocol", "json")
                .routeId("myRoute")
                .add();

        assertCollectionSize(context.getRouteDefinitions(), 1);
        assertCollectionSize(context.getRoutes(), 1);
        assertNotNull(context.getRoute("myRoute"));

        getMockEndpoint("mock:out").expectedMessageCount(1);

        template.sendBody("direct:in", "Hello Included Route");

        assertMockEndpointsSatisfied();
    }

    @Test
    void testRouteExcluded() {
        TemplatedRouteBuilder.builder(context, "myTemplateWithPrecondition")
                .parameter("protocol", "avro")
                .routeId("myRoute")
                .add();

        assertCollectionSize(context.getRouteDefinitions(), 0);
        assertCollectionSize(context.getRoutes(), 0);
        assertNull(context.getRoute("myRoute"));
    }

    @Test
    void testRouteIncludedByDefault() throws Exception {
        TemplatedRouteBuilder.builder(context, "myTemplateWithoutPrecondition")
                .routeId("myRoute")
                .add();

        assertCollectionSize(context.getRouteDefinitions(), 1);
        assertCollectionSize(context.getRoutes(), 1);
        assertNotNull(context.getRoute("myRoute"));

        getMockEndpoint("mock:out").expectedMessageCount(1);

        template.sendBody("direct:in", "Hello Included Route");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplateWithPrecondition")
                        .templateParameter("protocol")
                        .from("direct:in").precondition("'{{protocol}}' == 'json'")
                        .to("mock:out");
                routeTemplate("myTemplateWithoutPrecondition")
                        .from("direct:in")
                        .to("mock:out");
            }
        };
    }
}
