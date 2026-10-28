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

/**
 * The body-type console dumps what each step has been reached with (CAMEL-25750).
 */
public class BodyTypeDevConsoleTest extends ContextTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setMessageHistory(true);
                from("direct:start").routeId("orders")
                        .log("${body}").id("logIt")
                        .to("mock:result").id("result");
                from("direct:quiet").routeId("quiet")
                        .to("mock:quiet").id("quietResult");
            }
        };
    }

    @Test
    public void testTheStepsAMessageReached() {
        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", new LinkedHashMap<>(Map.of("id", 1)));

        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("body-type");
        Assertions.assertNotNull(con);
        Assertions.assertEquals("camel", con.getGroup());

        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON);
        JsonArray routes = out.getCollection("routes");
        // the quiet route has not been reached by a message
        Assertions.assertEquals(1, routes.size());
        JsonObject route = (JsonObject) routes.get(0);
        Assertions.assertEquals("orders", route.getString("routeId"));
        JsonArray steps = route.getCollection("steps");
        Assertions.assertEquals(2, steps.size());
        JsonObject result = (JsonObject) steps.get(1);
        Assertions.assertEquals("result", result.getString("id"));
        Assertions.assertEquals(List.of("java.lang.String", "java.util.LinkedHashMap"), result.getCollection("bodyTypes"));
        Assertions.assertEquals(List.of("payload", "data"), result.getCollection("bodyKinds"));

        String text = (String) con.call(DevConsole.MediaType.TEXT);
        Assertions.assertTrue(text.contains("Route: orders"), text);
        Assertions.assertTrue(text.contains("body: String | LinkedHashMap (payload and data)"), text);
    }

    @Test
    public void testOnlyTheVariedSteps() {
        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");
        template.sendBody("direct:quiet", "Hello");

        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("body-type");
        JsonObject all = (JsonObject) con.call(DevConsole.MediaType.JSON);
        Assertions.assertEquals(2, ((JsonArray) all.getCollection("routes")).size());

        // every step was reached with text only
        JsonObject varied = (JsonObject) con.call(DevConsole.MediaType.JSON, Map.of(BodyTypeDevConsole.VARIED, "true"));
        Assertions.assertTrue(((JsonArray) varied.getCollection("routes")).isEmpty());

        // a filter by route id
        JsonObject quiet = (JsonObject) con.call(DevConsole.MediaType.JSON, Map.of(BodyTypeDevConsole.FILTER, "quiet"));
        JsonArray routes = quiet.getCollection("routes");
        Assertions.assertEquals(1, routes.size());
        Assertions.assertEquals("quiet", ((JsonObject) routes.get(0)).getString("routeId"));
    }

    @Test
    public void testNothingReachedYet() {
        DevConsole con = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("body-type");
        JsonObject out = (JsonObject) con.call(DevConsole.MediaType.JSON);
        Assertions.assertTrue(((JsonArray) out.getCollection("routes")).isEmpty());

        String text = (String) con.call(DevConsole.MediaType.TEXT);
        Assertions.assertTrue(text.startsWith("No step has been reached"), text);
    }
}
