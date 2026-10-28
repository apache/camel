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

import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-25598: the route console shows the route template id and the source of the templated route of a route.
 */
public class RouteDevConsoleTemplatedRouteTest extends AbstractDevConsoleTest {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        RouteBuilder builder = new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("greeting").templateParameter("greeting")
                        .from("direct:greet")
                        .setBody(constant("{{greeting}}"));

                templatedRoute("greeting").routeId("greet").parameter("greeting", "Hello");

                from("direct:plain").routeId("plain")
                        .to("mock:plain");
            }
        };
        builder.setResource(ResourceHelper.fromString("mem:my-routes.java", ""));
        return builder;
    }

    @Test
    public void testTemplatedRouteJson() {
        DevConsole console = assertConsoleExists("route");

        JsonObject greet = route(callJson(console, Map.of(RouteDevConsole.FILTER, "greet")));
        assertEquals("greet", greet.getString("routeId"));
        assertEquals("greeting", greet.getString("routeTemplateId"));
        assertEquals("mem:my-routes.java", greet.getString("templatedRouteSource"));

        JsonObject plain = route(callJson(console, Map.of(RouteDevConsole.FILTER, "plain")));
        assertEquals("plain", plain.getString("routeId"));
        assertFalse(plain.containsKey("routeTemplateId"));
        assertFalse(plain.containsKey("templatedRouteSource"));
    }

    @Test
    public void testTemplatedRouteText() {
        DevConsole console = assertConsoleExists("route");

        String text = callText(console, Map.of(RouteDevConsole.FILTER, "greet"));
        assertTrue(text.contains("Route Template Id: greeting"), text);
        assertTrue(text.contains("Templated Route Source: mem:my-routes.java"), text);
    }

    private static JsonObject route(JsonObject json) {
        List<JsonObject> routes = json.getCollection("routes");
        assertEquals(1, routes.size());
        return routes.get(0);
    }
}
