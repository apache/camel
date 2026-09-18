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
package org.apache.camel.impl.console;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Routes created by a Kamelet are internal to the Kamelet and not registered with JMX by default, so the route console
 * does not list them; the context console's route counts must agree with that list rather than with the raw number of
 * routes in the context. The statistics themselves need camel-management on the classpath, which this module's tests do
 * not have, so the counting is asserted directly.
 */
public class ContextDevConsoleKameletRoutesTest extends ContextTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:app").routeId("app").to("mock:app");

                RouteDefinition internal = from("direct:internal").routeId("kamelet-internal");
                internal.to("mock:internal");
                internal.setKamelet(true);
            }
        };
    }

    @Test
    public void testKameletRoutesAreNotCountedAsRoutes() throws Exception {
        Assertions.assertEquals(2, context.getRoutes().size(), "both routes exist in the context");
        Assertions.assertTrue(context.getRoute("kamelet-internal").isCreatedByKamelet());

        int[] counts = ContextDevConsole.countRoutes(context);
        Assertions.assertEquals(1, counts[0], "total leaves out the Kamelet route");
        Assertions.assertEquals(1, counts[1], "started leaves out the Kamelet route");

        context.getRouteController().stopRoute("app");
        counts = ContextDevConsole.countRoutes(context);
        Assertions.assertEquals(1, counts[0]);
        Assertions.assertEquals(0, counts[1], "a stopped route still counts as total but not as started");
    }
}
