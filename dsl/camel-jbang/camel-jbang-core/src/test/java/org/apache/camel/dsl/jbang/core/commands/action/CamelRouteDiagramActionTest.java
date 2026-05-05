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

import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamelRouteDiagramActionTest {

    @Test
    void testParseRoutesEmpty() {
        JsonObject jo = new JsonObject();
        CamelRouteDiagramAction action = new CamelRouteDiagramAction(null);
        List<RouteInfo> routes = action.parseRoutes(jo);
        assertTrue(routes.isEmpty());
    }

    @Test
    void testParseRoutesWithData() {
        JsonObject line1 = new JsonObject();
        line1.put("type", "from");
        line1.put("code", "timer:tick");
        line1.put("level", 0);

        JsonObject line2 = new JsonObject();
        line2.put("type", "to");
        line2.put("code", "log:a");
        line2.put("level", 1);

        JsonArray code = new JsonArray();
        code.add(line1);
        code.add(line2);

        JsonObject routeObj = new JsonObject();
        routeObj.put("routeId", "route1");
        routeObj.put("code", code);

        JsonArray routesArr = new JsonArray();
        routesArr.add(routeObj);

        JsonObject jo = new JsonObject();
        jo.put("routes", routesArr);

        CamelRouteDiagramAction action = new CamelRouteDiagramAction(null);
        List<RouteInfo> routes = action.parseRoutes(jo);

        assertEquals(1, routes.size());
        assertEquals("route1", routes.get(0).routeId);
        assertEquals(2, routes.get(0).nodes.size());
        assertEquals("from", routes.get(0).nodes.get(0).type);
        assertEquals("timer:tick", routes.get(0).nodes.get(0).code);
    }
}
