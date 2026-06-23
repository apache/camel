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
package org.apache.camel.dsl.jbang.core.commands.action;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class RouteControllerActionTest extends ActionCommandTestSupport {

    @Test
    void testRequestsRouteControllerAndRendersRoutes() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        RouteControllerAction command = new RouteControllerAction(new CamelJBangMain().withPrinter(printer));
        command.name = "myApp";
        // options are normally defaulted by picocli; set them as we construct the command directly
        command.sort = "id";
        command.header = true;

        int exit = callWithResponse(command, defaultControllerResponse());

        assertEquals(0, exit);

        JsonObject action = readActionFile(TEST_PID);
        assertNotNull(action, "action file should be written for the matched process");
        assertEquals("route-controller", action.getString("action"));

        String out = printer.getOutput();
        assertTrue(out.contains("Default Route Controller"), "should print the controller header, was: " + out);
        assertTrue(out.contains("myRoute"), "should print the route id, was: " + out);
    }

    @Test
    void testReturnsErrorWhenNameDoesNotMatch() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        RouteControllerAction command = new RouteControllerAction(new CamelJBangMain().withPrinter(printer));
        command.name = "doesNotExist";

        int exit = callWithSingleProcess(command);

        assertEquals(1, exit);
        assertTrue(printer.getOutput().isEmpty(), "nothing should be rendered when no process matches");
    }

    private static JsonObject defaultControllerResponse() {
        JsonObject route = new JsonObject();
        route.put("routeId", "myRoute");
        route.put("uri", "timer://foo");
        route.put("status", "Started");
        JsonArray routes = new JsonArray();
        routes.add(route);

        JsonObject response = new JsonObject();
        response.put("controller", "DefaultRouteController");
        response.put("startingRoutes", false);
        response.put("totalRoutes", 1);
        response.put("routes", routes);
        return response;
    }
}
