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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class CamelRouteTopologyActionTest extends ActionCommandTestSupport {

    @Test
    void testRequestsTopologyAndRendersNodes() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelRouteTopologyAction command = new CamelRouteTopologyAction(new CamelJBangMain().withPrinter(printer));
        command.files = List.of("myApp");

        int exit = callWithResponse(command, singleNodeResponse());

        assertEquals(0, exit);

        JsonObject action = readActionFile(TEST_PID);
        assertNotNull(action, "action file should be written for the matched process");
        assertEquals("route-topology", action.getString("action"));

        String out = printer.getOutput();
        assertTrue(out.contains("Route Topology (1 routes, 0 connections)"),
                "should print the topology summary header, was: " + out);
        assertTrue(out.contains("myRoute"), "should print the route id, was: " + out);
        assertTrue(out.contains("timer://foo"), "should print the route source, was: " + out);
    }

    @Test
    void testReturnsErrorWhenNoRunningIntegrationAndNoFilesGiven() throws Exception {
        // no status file written, so the mocked process does not match "*" and files stays empty (the default)
        CamelRouteTopologyAction command = new CamelRouteTopologyAction(new CamelJBangMain().withPrinter(printer));

        int exit = callWithSingleProcess(command);

        assertEquals(1, exit);
        assertTrue(printer.getOutput().contains("No running Camel integration found"),
                "should report no running integration, was: " + printer.getOutput());
    }

    @Test
    void testReturnsErrorWhenNameDoesNotMatchAndIsNotASourceFile() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelRouteTopologyAction command = new CamelRouteTopologyAction(new CamelJBangMain().withPrinter(printer));
        // "doesNotExist" matches no running process, so it is tried as a source file next
        command.files = List.of("doesNotExist");

        int exit = callWithSingleProcess(command);

        assertEquals(1, exit);
        assertTrue(printer.getOutput().contains("File does not exist: doesNotExist"),
                "should report the missing source file, was: " + printer.getOutput());
    }

    @Test
    void testMultipleFilesSkipRunningIntegrationLookupEvenWhenFirstNameMatches() throws Exception {
        // a running integration whose name matches the first of the given file tokens: with 2+ tokens the
        // running-integration lookup (findPids) must not even be attempted, so no ProcessHandle mocking is needed
        // here at all -- that absence of interaction is exactly what this test proves
        writeStatusFile(TEST_PID, "route1");

        CamelRouteTopologyAction command = new CamelRouteTopologyAction(new CamelJBangMain().withPrinter(printer));
        command.files = List.of("route1", "route2");

        int exit = command.doCall();

        assertEquals(1, exit);
        assertTrue(printer.getOutput().contains("File does not exist: route1"),
                "with 2+ file args, dispatch must go straight to source-file handling and skip the "
                                                                                + "running-integration lookup entirely, was: "
                                                                                + printer.getOutput());
    }

    // Note: the actual doCallSource -> Run.runTransform spawn path (successfully rendering topology from a real
    // source file) is out of scope for unit tests, mirroring CamelRouteStructureActionTest: it boots a transient
    // Camel and cannot be exercised deterministically here. The JSON-shape rendering it feeds into is covered by
    // CamelRouteTopologyActionRenderTest, and DefaultDumpRoutesStrategyTopologyJsonTest (camel-core) proves the
    // route-topology.json that doCallSource reads is produced correctly from the route model alone.

    private static JsonObject singleNodeResponse() {
        JsonObject node = new JsonObject();
        node.put("routeId", "myRoute");
        node.put("from", "timer://foo");
        node.put("nodeType", "route");
        JsonArray nodes = new JsonArray();
        nodes.add(node);

        JsonObject response = new JsonObject();
        response.put("nodes", nodes);
        response.put("edges", new JsonArray());
        return response;
    }
}
