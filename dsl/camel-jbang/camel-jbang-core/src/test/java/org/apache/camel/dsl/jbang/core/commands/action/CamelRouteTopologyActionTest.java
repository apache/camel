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
class CamelRouteTopologyActionTest extends ActionCommandTestSupport {

    @Test
    void testRequestsTopologyAndRendersNodes() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelRouteTopologyAction command = new CamelRouteTopologyAction(new CamelJBangMain().withPrinter(printer));
        command.name = "myApp";

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
    void testReturnsErrorWhenNameDoesNotMatch() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        CamelRouteTopologyAction command = new CamelRouteTopologyAction(new CamelJBangMain().withPrinter(printer));
        command.name = "doesNotExist";

        int exit = callWithSingleProcess(command);

        assertEquals(1, exit);
        assertTrue(printer.getOutput().contains("No running Camel integration found"),
                "should report no running integration, was: " + printer.getOutput());
    }

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
