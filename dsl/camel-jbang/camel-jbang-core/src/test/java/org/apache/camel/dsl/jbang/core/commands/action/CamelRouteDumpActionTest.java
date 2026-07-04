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
class CamelRouteDumpActionTest extends ActionCommandTestSupport {

    @Test
    void testRequestsRouteDumpAndRendersSource() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelRouteDumpAction command = new CamelRouteDumpAction(new CamelJBangMain().withPrinter(printer));
        command.name = "myApp";
        // options are normally defaulted by picocli; set them as we construct the command directly
        command.format = "yaml";
        command.sort = "name";

        int exit = callWithResponse(command, singleRouteResponse());

        assertEquals(0, exit);

        // the command must request a route-dump with the default yaml format
        JsonObject action = readActionFile(TEST_PID);
        assertNotNull(action, "action file should be written for the matched process");
        assertEquals("route-dump", action.getString("action"));
        assertEquals("yaml", action.getString("format"));

        // the simulated response must be rendered as source code
        String out = printer.getOutput();
        assertTrue(out.contains("Source: myroute.yaml"), "should print the source filename, was: " + out);
        assertTrue(out.contains("from uri: timer:foo"), "should print the route code, was: " + out);
    }

    @Test
    void testReturnsErrorWhenNameDoesNotMatch() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelRouteDumpAction command = new CamelRouteDumpAction(new CamelJBangMain().withPrinter(printer));
        command.name = "doesNotExist";

        // no process matches, so the command returns before requesting any output
        int exit = callWithSingleProcess(command);

        assertEquals(1, exit);
        assertTrue(printer.getOutput().isEmpty(), "nothing should be rendered when no process matches");
    }

    private static JsonObject singleRouteResponse() {
        JsonObject code = new JsonObject();
        code.put("line", 1);
        code.put("code", "from uri: timer:foo");
        JsonArray codeLines = new JsonArray();
        codeLines.add(code);

        JsonObject route = new JsonObject();
        route.put("source", "myroute.yaml");
        route.put("routeId", "myRoute");
        route.put("code", codeLines);
        JsonArray routes = new JsonArray();
        routes.add(route);

        JsonObject response = new JsonObject();
        response.put("routes", routes);
        return response;
    }
}
