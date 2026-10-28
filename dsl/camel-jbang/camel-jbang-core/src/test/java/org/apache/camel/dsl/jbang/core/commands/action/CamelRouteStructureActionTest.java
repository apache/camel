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

import java.util.List;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void testShowsTheBodyTypesEachStepWasReachedWith() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelRouteStructureAction command = new CamelRouteStructureAction(new CamelJBangMain().withPrinter(printer));
        command.name = "myApp";
        command.sort = "name";

        int exit = callWithResponse(command, bodyTypesResponse());

        assertEquals(0, exit);
        String out = printer.getOutput();
        assertTrue(out.contains("body: GenericFile"), out);
        // a step reached with a Map by some messages and with a text by others shows both, and their kinds
        assertTrue(out.contains("body: LinkedHashMap | String (data and payload)"), out);
        // two types of one kind are dynamic, but need no attention
        String log = out.lines().filter(l -> l.contains("log[${body}]")).findFirst().orElse("");
        assertTrue(log.endsWith("body: String | byte[]"), log);
        // the types line up in a column after the code
        assertEquals(1, out.lines().filter(l -> l.contains("body:")).map(l -> l.indexOf("body:")).distinct().count(),
                out);
        // the from has no body type of its own (its body is what the first step was reached with)
        String from = out.lines().filter(l -> l.contains("from[file:inbox]")).findFirst().orElse("");
        assertFalse(from.contains("body:"), from);
    }

    @Test
    void testVariedShowsOnlyTheStepsReachedWithMoreThanOneBodyType() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelRouteStructureAction command = new CamelRouteStructureAction(new CamelJBangMain().withPrinter(printer));
        command.name = "myApp";
        command.sort = "name";
        command.varied = true;

        int exit = callWithResponse(command, bodyTypesResponse());

        assertEquals(0, exit);
        String out = printer.getOutput();
        assertTrue(out.contains("Source: orders.camel.yaml"), out);
        assertTrue(out.contains("to[http://stock/api/reserve]"), out);
        assertTrue(out.contains("log[${body}]"), out);
        // the steps reached with one type, and the route and its from, are left out
        assertFalse(out.contains("unmarshal[json]"), out);
        assertFalse(out.contains("from[file:inbox]"), out);
    }

    @Test
    void testVariedSaysWhenNoStepVaried() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelRouteStructureAction command = new CamelRouteStructureAction(new CamelJBangMain().withPrinter(printer));
        command.name = "myApp";
        command.sort = "name";
        command.varied = true;

        int exit = callWithResponse(command, singleRouteResponse());

        assertEquals(0, exit);
        assertTrue(printer.getOutput().contains("No step has been reached with more than one body type"),
                printer.getOutput());
    }

    @Test
    void testOnlyVariedKeepsTheJsonOfTheVariedSteps() {
        JsonObject varied = CamelRouteStructureAction.onlyVaried(bodyTypesResponse());

        JsonArray routes = varied.getCollection("routes");
        assertEquals(1, routes.size());
        JsonArray code = ((JsonObject) routes.get(0)).getCollection("code");
        assertEquals(2, code.size());
        assertEquals("to1", ((JsonObject) code.get(0)).getString("id"));
        assertEquals("log1", ((JsonObject) code.get(1)).getString("id"));
        // the route itself keeps its other fields
        assertEquals("orders.camel.yaml", ((JsonObject) routes.get(0)).getString("source"));

        assertTrue(((JsonArray) CamelRouteStructureAction.onlyVaried(singleRouteResponse()).getCollection("routes"))
                .isEmpty());
    }

    @Test
    void testRawShowsOnlyTheCode() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelRouteStructureAction command = new CamelRouteStructureAction(new CamelJBangMain().withPrinter(printer));
        command.name = "myApp";
        command.sort = "name";
        command.raw = true;

        int exit = callWithResponse(command, bodyTypesResponse());

        assertEquals(0, exit);
        String out = printer.getOutput();
        assertTrue(out.contains("unmarshal[json]"), out);
        assertFalse(out.contains("body:"), out);
    }

    @Test
    void testBodyTypesBySimpleNames() {
        assertEquals("String | byte[] | String[]",
                CamelRouteStructureAction.bodyTypes(List.of("java.lang.String", "byte[]", "java.lang.String[]"),
                        List.of("payload")));
        assertEquals("null | String | LinkedHashMap (null, payload and data)",
                CamelRouteStructureAction.bodyTypes(List.of("null", "java.lang.String", "java.util.LinkedHashMap"),
                        List.of("null", "payload", "data")));
        // an older Camel says no kinds
        assertEquals("String | LinkedHashMap",
                CamelRouteStructureAction.bodyTypes(List.of("java.lang.String", "java.util.LinkedHashMap"), null));
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

    private static JsonObject bodyTypesResponse() {
        JsonArray codeLines = new JsonArray();
        codeLines.add(codeLine(1, "route", "orders", 0, "route[orders]", null, null));
        codeLines.add(codeLine(2, "from", "orders", 1, "from[file:inbox]", null, null));
        codeLines.add(codeLine(5, "unmarshal", "unmarshal1", 2, "unmarshal[json]",
                List.of("org.apache.camel.component.file.GenericFile"), List.of("payload")));
        codeLines.add(codeLine(8, "to", "to1", 2, "to[http://stock/api/reserve]",
                List.of("java.util.LinkedHashMap", "java.lang.String"), List.of("data", "payload")));
        codeLines.add(codeLine(11, "log", "log1", 2, "log[${body}]",
                List.of("java.lang.String", "byte[]"), List.of("payload")));

        JsonObject route = new JsonObject();
        route.put("source", "orders.camel.yaml");
        route.put("routeId", "orders");
        route.put("code", codeLines);
        JsonArray routes = new JsonArray();
        routes.add(route);

        JsonObject response = new JsonObject();
        response.put("routes", routes);
        return response;
    }

    private static JsonObject codeLine(
            int line, String type, String id, int level, String code, List<String> bodyTypes, List<String> bodyKinds) {
        JsonObject jo = new JsonObject();
        jo.put("line", line);
        jo.put("type", type);
        jo.put("id", id);
        jo.put("level", level);
        jo.put("code", code);
        if (bodyTypes != null) {
            jo.put("bodyTypes", new JsonArray(bodyTypes));
            jo.put("bodyKinds", new JsonArray(bodyKinds));
        }
        return jo;
    }
}
