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

import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeLabelMode;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamelRouteDiagramActionTest {

    @Test
    void testParseRoutesEmpty() {
        org.apache.camel.util.json.JsonObject jo = new org.apache.camel.util.json.JsonObject();
        CamelRouteDiagramAction action = new CamelRouteDiagramAction(null);
        List<RouteInfo> routes = action.parseRoutes(jo);
        assertTrue(routes.isEmpty());
    }

    @Test
    void testParseRoutesWithData() {
        org.apache.camel.util.json.JsonObject line1 = new org.apache.camel.util.json.JsonObject();
        line1.put("type", "from");
        line1.put("code", "timer:tick");
        line1.put("level", 0);

        org.apache.camel.util.json.JsonObject line2 = new org.apache.camel.util.json.JsonObject();
        line2.put("type", "to");
        line2.put("code", "log:a");
        line2.put("level", 1);

        org.apache.camel.util.json.JsonArray code = new org.apache.camel.util.json.JsonArray();
        code.add(line1);
        code.add(line2);

        org.apache.camel.util.json.JsonObject routeObj = new org.apache.camel.util.json.JsonObject();
        routeObj.put("routeId", "route1");
        routeObj.put("code", code);

        org.apache.camel.util.json.JsonArray routesArr = new org.apache.camel.util.json.JsonArray();
        routesArr.add(routeObj);

        org.apache.camel.util.json.JsonObject jo = new org.apache.camel.util.json.JsonObject();
        jo.put("routes", routesArr);

        CamelRouteDiagramAction action = new CamelRouteDiagramAction(null);
        List<RouteInfo> routes = action.parseRoutes(jo);

        assertEquals(1, routes.size());
        assertEquals("route1", routes.get(0).routeId);
        assertEquals(2, routes.get(0).nodes.size());
        assertEquals("from", routes.get(0).nodes.get(0).type);
        assertEquals("timer:tick", routes.get(0).nodes.get(0).code);
    }

    @Test
    void testParseRoutesWithDescription() {
        org.apache.camel.util.json.JsonObject line1 = new org.apache.camel.util.json.JsonObject();
        line1.put("type", "from");
        line1.put("code", "timer:tick?period=1000");
        line1.put("description", "Poll every second");
        line1.put("level", 0);

        org.apache.camel.util.json.JsonObject line2 = new org.apache.camel.util.json.JsonObject();
        line2.put("type", "to");
        line2.put("code", "log:a");
        line2.put("level", 1);

        org.apache.camel.util.json.JsonArray code = new org.apache.camel.util.json.JsonArray();
        code.add(line1);
        code.add(line2);

        org.apache.camel.util.json.JsonObject routeObj = new org.apache.camel.util.json.JsonObject();
        routeObj.put("routeId", "route1");
        routeObj.put("code", code);

        org.apache.camel.util.json.JsonArray routesArr = new org.apache.camel.util.json.JsonArray();
        routesArr.add(routeObj);

        org.apache.camel.util.json.JsonObject jo = new org.apache.camel.util.json.JsonObject();
        jo.put("routes", routesArr);

        CamelRouteDiagramAction action = new CamelRouteDiagramAction(null);
        List<RouteInfo> routes = action.parseRoutes(jo);

        assertEquals("Poll every second", routes.get(0).nodes.get(0).description);
        assertNull(routes.get(0).nodes.get(1).description);
    }

    @Test
    void testParseNodeLabelMode() {
        assertEquals(NodeLabelMode.CODE, CamelRouteDiagramAction.parseNodeLabelMode(null));
        assertEquals(NodeLabelMode.CODE, CamelRouteDiagramAction.parseNodeLabelMode(""));
        assertEquals(NodeLabelMode.CODE, CamelRouteDiagramAction.parseNodeLabelMode("code"));
        assertEquals(NodeLabelMode.CODE, CamelRouteDiagramAction.parseNodeLabelMode("CODE"));
        assertEquals(NodeLabelMode.DESCRIPTION, CamelRouteDiagramAction.parseNodeLabelMode("description"));
        assertEquals(NodeLabelMode.DESCRIPTION, CamelRouteDiagramAction.parseNodeLabelMode("Description"));
        assertEquals(NodeLabelMode.BOTH, CamelRouteDiagramAction.parseNodeLabelMode("both"));
        assertEquals(NodeLabelMode.BOTH, CamelRouteDiagramAction.parseNodeLabelMode("BOTH"));
        assertEquals(NodeLabelMode.CODE, CamelRouteDiagramAction.parseNodeLabelMode("invalid"));
    }
}
