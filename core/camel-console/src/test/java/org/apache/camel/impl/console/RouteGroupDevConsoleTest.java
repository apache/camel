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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * RouteGroupDevConsole is driven by {@code ManagedCamelContext}, which camel-console has no dependency on - so the
 * route group list is always empty here. This test only verifies the console's basic shape.
 */
public class RouteGroupDevConsoleTest extends ContextTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("myRoute").to("mock:result");
            }
        };
    }

    @Test
    public void testRouteGroupConsoleText() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-group");
        Assertions.assertNotNull(con);
        Assertions.assertEquals("camel", con.getGroup());
        Assertions.assertEquals("route-group", con.getId());

        String out = (String) con.call(DevConsole.MediaType.TEXT);
        Assertions.assertNotNull(out);
    }

    @Test
    public void testRouteGroupConsoleJson() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-group");
        Assertions.assertNotNull(con);

        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON);
        Assertions.assertNotNull(out);

        JsonArray routeGroups = out.getCollection("routeGroups");
        Assertions.assertNotNull(routeGroups);
    }

    @Test
    public void testRouteGroupConsoleWithAction() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-group");
        Assertions.assertNotNull(con);

        Map<String, Object> options = new HashMap<>();
        options.put(RouteGroupDevConsole.ACTION, "start");

        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON, options);
        Assertions.assertNotNull(out);
        Assertions.assertTrue(out.isEmpty());
    }
}
