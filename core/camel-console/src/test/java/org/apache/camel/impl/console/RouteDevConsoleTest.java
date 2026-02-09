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

import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for RouteDevConsole with various options (filter, limit, processors, action).
 */
public class RouteDevConsoleTest extends AbstractDevConsoleTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("myRoute")
                        .to("log:foo")
                        .to("mock:result");

                from("direct:bar").routeId("barRoute").routeGroup("myGroup")
                        .to("mock:bar");
            }
        };
    }

    @Test
    public void testRouteConsoleBasic() {
        DevConsole console = assertConsoleExists("route", "camel");
        callText(console);

        JsonObject json = callJson(console);
        assertTrue(json.containsKey("routes"));
    }

    @Test
    public void testRouteConsoleWithFilter() {
        DevConsole console = assertConsoleExists("route");

        callText(console, Map.of(RouteDevConsole.FILTER, "myRoute"));
        callJson(console, Map.of(RouteDevConsole.FILTER, "myRoute"));
    }

    @Test
    public void testRouteConsoleWithLimit() {
        DevConsole console = assertConsoleExists("route");

        callText(console, Map.of(RouteDevConsole.LIMIT, "1"));
        callJson(console, Map.of(RouteDevConsole.LIMIT, "1"));
    }

    @Test
    public void testRouteConsoleWithProcessors() {
        DevConsole console = assertConsoleExists("route");

        callText(console, Map.of(RouteDevConsole.PROCESSORS, "true"));
        callJson(console, Map.of(RouteDevConsole.PROCESSORS, "true"));
    }

    @Test
    public void testRouteConsoleWithGroupFilter() {
        DevConsole console = assertConsoleExists("route");
        callText(console, Map.of(RouteDevConsole.FILTER, "myGroup"));
    }

    @Test
    public void testRouteConsoleWithWildcardFilter() {
        DevConsole console = assertConsoleExists("route");
        callText(console, Map.of(RouteDevConsole.FILTER, "*Route"));
    }

    @Test
    public void testRouteConsoleActionStop() {
        DevConsole console = assertConsoleExists("route");
        callText(console, Map.of(RouteDevConsole.ACTION, "stop", RouteDevConsole.FILTER, "barRoute"));
    }
}
