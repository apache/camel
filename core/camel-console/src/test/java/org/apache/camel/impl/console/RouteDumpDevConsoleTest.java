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

import java.io.StringReader;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * RouteDumpDevConsole is driven by {@code ManagedCamelContext}, which camel-console has no dependency on - so the route
 * list is always empty here. This test only verifies the console's basic shape.
 */
public class RouteDumpDevConsoleTest extends ContextTestSupport {

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
    public void testRouteDumpConsoleText() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-dump");
        Assertions.assertNotNull(con);
        Assertions.assertEquals("camel", con.getGroup());
        Assertions.assertEquals("route-dump", con.getId());

        String out = (String) con.call(DevConsole.MediaType.TEXT);
        Assertions.assertNotNull(out);
    }

    @Test
    public void testRouteDumpConsoleJson() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-dump");
        Assertions.assertNotNull(con);

        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON);
        Assertions.assertNotNull(out);

        JsonArray routes = out.getCollection("routes");
        Assertions.assertNotNull(routes);
    }

    @Test
    public void testYamlSourceLocationOfAListItem() {
        // the source location of a when comes first in its list item: dropping it must keep the dash
        String yaml = """
                - route:
                    from:
                      sourceLineNumber: 7
                      sourceLocation: OrderRoute.java
                      uri: timer:orders
                      steps:
                        - choice:
                            sourceLineNumber: 13
                            sourceLocation: OrderRoute.java
                            when:
                              - sourceLineNumber: 14
                                sourceLocation: OrderRoute.java
                                expression:
                                  simple:
                                    expression: "${body} == 9"
                                steps:
                                  - throwException:
                                      sourceLineNumber: 15
                                      sourceLocation: OrderRoute.java
                """;
        List<RouteDumpDevConsole.CodeLine> code = RouteDumpDevConsole.javaOrYamlLoadSourceAsJson(new StringReader(yaml));
        List<String> lines = code.stream().map(c -> c.line() + " " + Jsoner.unescape(c.code())).toList();
        Assertions.assertEquals(List.of(
                "-1 - route:",
                "7     from:",
                "-1       uri: timer:orders",
                "-1       steps:",
                "13         - choice:",
                "-1             when:",
                "14               - expression:",
                "-1                   simple:",
                "-1                     expression: \"${body} == 9\"",
                "-1                 steps:",
                "15                   - throwException:"), lines);
    }
}
