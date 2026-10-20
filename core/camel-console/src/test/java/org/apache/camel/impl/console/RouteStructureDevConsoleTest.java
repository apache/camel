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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RouteStructureDevConsoleTest extends ContextTestSupport {

    @Override
    protected boolean useJmx() {
        // the route console lists the routes from JMX
        return true;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setMessageHistory(true);
                from("direct:start").routeId("myRoute").to("mock:result").id("result");
            }
        };
    }

    @Test
    public void testBodyTypesAcrossMessages() {
        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", 123);

        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-structure");
        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON);
        JsonArray routes = out.getCollection("routes");
        JsonObject route = (JsonObject) routes.get(0);
        JsonArray code = route.getCollection("code");

        // the route and its from have no body type: the body the from gave is what the first processor was reached with
        Assertions.assertNull(((JsonObject) code.get(0)).get("bodyTypes"));
        Assertions.assertNull(((JsonObject) code.get(1)).get("bodyTypes"));
        JsonObject to = (JsonObject) code.get(2);
        Assertions.assertEquals("result", to.getString("id"));
        Assertions.assertEquals(List.of("java.lang.String", "java.lang.Integer"), to.getCollection("bodyTypes"));
        // a number converts to text, so it is of the same kind as the text: dynamic, but no attention needed
        Assertions.assertEquals(List.of("payload"), to.getCollection("bodyKinds"));

        String text = (String) con.call(DevConsole.MediaType.TEXT);
        Assertions.assertTrue(text.contains("body: String | Integer"), text);
        Assertions.assertFalse(text.contains("(payload"), text);
    }

    @Test
    public void testBodiesOfMoreThanOneKindNeedAttention() {
        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", new LinkedHashMap<>(Map.of("id", 1)));

        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-structure");
        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON);
        JsonObject route = (JsonObject) ((JsonArray) out.getCollection("routes")).get(0);
        JsonObject to = (JsonObject) ((JsonArray) route.getCollection("code")).get(2);
        Assertions.assertEquals(List.of("java.lang.String", "java.util.LinkedHashMap"), to.getCollection("bodyTypes"));
        Assertions.assertEquals(List.of("payload", "data"), to.getCollection("bodyKinds"));

        String text = (String) con.call(DevConsole.MediaType.TEXT);
        Assertions.assertTrue(text.contains("body: String | LinkedHashMap (payload and data)"), text);

        // the route console counts the steps per route, so a tool that lists the routes sees them
        DevConsole routeConsole = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route");
        JsonObject routesOut = (JsonObject) routeConsole.call(DevConsole.MediaType.JSON);
        JsonObject myRoute = (JsonObject) ((JsonArray) routesOut.getCollection("routes")).get(0);
        Assertions.assertEquals(1, myRoute.getInteger("variedBodyTypes"));
        Assertions.assertEquals(1, myRoute.getInteger("mixedBodyKinds"));
    }

    @Test
    public void testMixedKindsInWords() {
        Assertions.assertEquals("payload and data", RouteStructureDevConsole.mixedKindsText(List.of("payload", "data")));
        Assertions.assertEquals("null, payload and data",
                RouteStructureDevConsole.mixedKindsText(List.of("null", "payload", "data")));
    }

    @Test
    public void testNoBodyTypesBeforeAnyMessage() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-structure");
        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON);
        JsonArray routes = out.getCollection("routes");
        JsonArray code = ((JsonObject) routes.get(0)).getCollection("code");
        for (Object line : code) {
            Assertions.assertNull(((JsonObject) line).get("bodyTypes"));
        }

        String text = (String) con.call(DevConsole.MediaType.TEXT);
        Assertions.assertFalse(text.contains("body:"), text);

        // nothing varied: the route console has no counts
        DevConsole routeConsole = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route");
        JsonObject routesOut = (JsonObject) routeConsole.call(DevConsole.MediaType.JSON);
        JsonObject myRoute = (JsonObject) ((JsonArray) routesOut.getCollection("routes")).get(0);
        Assertions.assertNull(myRoute.get("variedBodyTypes"));
        Assertions.assertNull(myRoute.get("mixedBodyKinds"));
    }

    @Test
    public void testRouteStructureConsoleText() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-structure");
        Assertions.assertNotNull(con);
        Assertions.assertEquals("camel", con.getGroup());
        Assertions.assertEquals("route-structure", con.getId());

        String out = (String) con.call(DevConsole.MediaType.TEXT);
        Assertions.assertNotNull(out);
    }

    @Test
    public void testRouteStructureConsoleJson() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-structure");
        Assertions.assertNotNull(con);

        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON);
        Assertions.assertNotNull(out);

        JsonArray routes = out.getCollection("routes");
        Assertions.assertNotNull(routes);
        Assertions.assertFalse(routes.isEmpty());

        JsonObject route = (JsonObject) routes.get(0);
        Assertions.assertEquals("myRoute", route.getString("routeId"));
        Assertions.assertNotNull(route.getString("from"));
    }
}
