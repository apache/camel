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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * CAMEL-25771: the route consoles have the checksum of each route as written, and the route console its revision, so a
 * tool can tell which routes changed and which version of a route the structure or the dump is of.
 */
public class RouteChecksumDevConsoleTest extends ContextTestSupport {

    @Override
    protected boolean useJmx() {
        // the route and route-dump consoles list the routes from JMX
        return true;
    }

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
    public void testChecksumInTheRouteConsoles() {
        String checksum = context.getRoute("myRoute").getChecksum();
        Assertions.assertNotNull(checksum);

        JsonObject route = firstRoute("route");
        Assertions.assertEquals(checksum, route.getString("checksum"));
        Assertions.assertEquals(1, route.getInteger("revision"));
        String text = (String) console("route").call(DevConsole.MediaType.TEXT);
        Assertions.assertTrue(text.contains("Checksum: " + checksum + " (revision 1)"), text);

        Assertions.assertEquals(checksum, firstRoute("route-structure").getString("checksum"));
        text = (String) console("route-structure").call(DevConsole.MediaType.TEXT);
        Assertions.assertTrue(text.contains("Checksum: " + checksum), text);

        Assertions.assertEquals(checksum, firstRoute("route-dump").getString("checksum"));
    }

    private JsonObject firstRoute(String id) {
        JsonObject out = (JsonObject) console(id).call(DevConsole.MediaType.JSON);
        return (JsonObject) ((JsonArray) out.getCollection("routes")).get(0);
    }

    private DevConsole console(String id) {
        return PluginHelper.getDevConsoleResolver(context).resolveDevConsole(id);
    }
}
