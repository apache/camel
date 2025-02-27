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

/**
 * The test ensuring that the precondition set on a route configuration determines if the route configuration is
 * included or not
 */
class RouteConfigurationPreconditionTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/RouteConfigurationPreconditionTest.xml");
    }

    @Test
    void testRouteConfigurationAreIncludedOrExcludedAsExpected() throws Exception {
        assertCollectionSize(context.getRouteConfigurationDefinitions(), 2);
        assertCollectionSize(context.getRouteDefinitions(), 2);
        assertCollectionSize(context.getRoutes(), 2);

        getMockEndpoint("mock:error").expectedMessageCount(2);
        getMockEndpoint("mock:error").expectedBodiesReceived("Activated", "Default");

        template.sendBody("direct:start", "Hello Activated Route Config");
        template.sendBody("direct:start2", "Hello Not Activated Route Config");

        assertMockEndpointsSatisfied();
    }
}
