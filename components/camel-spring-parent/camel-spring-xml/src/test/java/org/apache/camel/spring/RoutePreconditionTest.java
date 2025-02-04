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
package org.apache.camel.spring;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The test ensuring that the precondition set on a route determines if the route is included or not.
 */
class RoutePreconditionTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/RoutePreconditionTest.xml");
    }

    @Test
    void testRoutesAreIncludedOrExcludedAsExpected() throws Exception {
        assertCollectionSize(context.getRouteDefinitions(), 2);
        assertCollectionSize(context.getRoutes(), 2);
        assertNotNull(context.getRoute("templatedRouteIncluded"));
        assertNotNull(context.getRoute("routeIncluded"));
        assertNull(context.getRoute("templatedRouteExcluded"));
        assertNull(context.getRoute("routeExcluded"));

        getMockEndpoint("mock:out").expectedMessageCount(1);
        getMockEndpoint("mock:outT").expectedMessageCount(1);

        template.sendBody("direct:in", "Hello Included Route");
        template.sendBody("direct:inT", "Hello Included Templated Route");

        assertMockEndpointsSatisfied();
    }
}
