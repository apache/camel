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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RouteControllerConsoleTest extends AbstractDevConsoleTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("myRoute").to("mock:result");
            }
        };
    }

    @Test
    public void testDefaultRouteController() {
        DevConsole con = assertConsoleExists("route-controller", "camel");

        JsonObject out = callJson(con);
        assertEquals("DefaultRouteController", out.getString("controller"));
        assertFalse(out.containsKey("startingRoutes"));

        JsonArray routes = out.getJsonArray("routes");
        assertNotNull(routes);
        JsonObject route = (JsonObject) routes.get(0);
        assertEquals("myRoute", route.getString("routeId"));
        assertNotNull(route.getString("status"));
        assertFalse(route.containsKey("supervising"));
    }
}
