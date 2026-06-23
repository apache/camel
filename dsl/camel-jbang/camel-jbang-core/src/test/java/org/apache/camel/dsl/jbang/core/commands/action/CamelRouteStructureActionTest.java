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
class CamelRouteStructureActionTest extends ActionCommandTestSupport {

    @Test
    void testRequestsRouteStructureFromNamedIntegrationAndRendersSource() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelRouteStructureAction command = new CamelRouteStructureAction(new CamelJBangMain().withPrinter(printer));
        command.name = "myApp";
        // options are normally defaulted by picocli; set them as we construct the command directly
        command.sort = "name";

        int exit = callWithResponse(command, singleRouteResponse());

        assertEquals(0, exit);

        JsonObject action = readActionFile(TEST_PID);
        assertNotNull(action, "action file should be written for the matched process");
        assertEquals("route-structure", action.getString("action"));

        String out = printer.getOutput();
        assertTrue(out.contains("Source: myroute.yaml"), "should print the source filename, was: " + out);
        assertTrue(out.contains("from[timer://foo]"), "should print the route structure code, was: " + out);
    }

    // Note: there is no no-match test here. When no pid matches, the command falls back to doCallSource, which
    // spawns a background Camel via Run to compile source files. That spawn path is out of scope for this batch
    // (it cannot be exercised deterministically in a unit test), so only the named-pid round-trip branch is covered.

    private static JsonObject singleRouteResponse() {
        JsonObject code = new JsonObject();
        code.put("line", 1);
        code.put("type", "from");
        code.put("id", "from1");
        code.put("level", 0);
        code.put("code", "from[timer://foo]");
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
