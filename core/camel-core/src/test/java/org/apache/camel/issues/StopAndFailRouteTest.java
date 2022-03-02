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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.RouteError;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StopAndFailRouteTest extends ContextTestSupport {

    @Test
    public void stopRoute() throws Exception {
        Route route = context.getRoute("foo");
        Assertions.assertNull(route.getLastError());

        context.getRouteController().stopRoute("foo");

        assertEquals("Stopped", context.getRouteController().getRouteStatus("foo").name());

        RouteError re = route.getLastError();
        Assertions.assertNull(re);
    }

    @Test
    public void failRoute() throws Exception {
        Route route = context.getRoute("bar");
        Assertions.assertNull(route.getLastError());

        Throwable cause = new IllegalArgumentException("Forced");
        context.getRouteController().stopRoute("bar", cause);

        assertEquals("Stopped", context.getRouteController().getRouteStatus("bar").name());

        RouteError re = route.getLastError();
        Assertions.assertNotNull(re);
        Assertions.assertTrue(re.isUnhealthy());
        Assertions.assertEquals(RouteError.Phase.STOP, re.getPhase());
        Assertions.assertSame(cause, re.getException());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").routeId("foo")
                        .to("mock:foo");

                from("direct:bar").routeId("bar")
                        .to("mock:bar");
            }
        };
    }
}
