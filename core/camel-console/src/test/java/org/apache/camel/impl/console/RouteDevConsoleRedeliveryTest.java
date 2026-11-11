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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * CAMEL-26698: the route console has the redeliveries and the failures handled of the routes and their processors, so
 * an exchange that failed and then succeeded on redelivery leaves a trace.
 */
class RouteDevConsoleRedeliveryTest extends ContextTestSupport {

    @Override
    protected boolean useJmx() {
        // the route console reads the statistics from JMX
        return true;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(2).redeliveryDelay(0));

                // fails the first attempt of each exchange, so the redelivery succeeds
                from("direct:flaky").routeId("flakyRoute")
                        .process(e -> {
                            if (e.getMessage().getHeader(Exchange.REDELIVERY_COUNTER, 0, Integer.class) == 0) {
                                throw new IllegalStateException("first attempt");
                            }
                        }).id("flaky")
                        .to("mock:result").id("result");

                // fails every attempt, so the dead letter channel handles it
                from("direct:broken").routeId("brokenRoute")
                        .throwException(new IllegalStateException("always")).id("broken");
            }
        };
    }

    @Test
    void testRedeliveriesAndFailuresHandled() {
        template.sendBody("direct:flaky", "a");
        template.sendBody("direct:flaky", "b");
        template.sendBody("direct:broken", "c");

        // the route counts the exchanges that completed after a redelivery, the processor each redelivery
        JsonObject flaky = route("flakyRoute");
        JsonObject stats = flaky.getMap("statistics");
        Assertions.assertEquals(2, stats.getLong("redeliveries"));
        Assertions.assertEquals(0, stats.getLong("failuresHandled"));
        Assertions.assertEquals(0, stats.getLong("exchangesFailed"));
        Assertions.assertEquals(0, stats.getLong("externalRedeliveries"));
        // the redeliveries are counted at the step that was retried, not at the next one
        Assertions.assertEquals(2, processor(flaky, "flaky").getLong("redeliveries"));
        Assertions.assertEquals(0, processor(flaky, "result").getLong("redeliveries"));

        JsonObject broken = route("brokenRoute");
        stats = broken.getMap("statistics");
        Assertions.assertEquals(1, stats.getLong("failuresHandled"));
        Assertions.assertEquals(0, stats.getLong("exchangesFailed"));
        JsonObject step = processor(broken, "broken");
        Assertions.assertEquals(3, step.getLong("exchangesFailed"));
        Assertions.assertEquals(2, step.getLong("redeliveries"));

        String text = (String) console().call(DevConsole.MediaType.TEXT,
                Map.of(RouteDevConsole.FILTER, "brokenRoute", RouteDevConsole.PROCESSORS, "true"));
        Assertions.assertTrue(text.contains("Failures Handled: 1"), text);
        // the processor of the route, indented
        Assertions.assertTrue(text.contains("        Redeliveries: 2"), text);
        Assertions.assertTrue(text.contains("External Redeliveries: 0"), text);
    }

    private JsonObject route(String routeId) {
        JsonObject out = (JsonObject) console().call(DevConsole.MediaType.JSON,
                Map.of(RouteDevConsole.FILTER, routeId, RouteDevConsole.PROCESSORS, "true"));
        return (JsonObject) ((JsonArray) out.getCollection("routes")).get(0);
    }

    private static JsonObject processor(JsonObject route, String id) {
        for (Object o : route.getCollection("processors")) {
            JsonObject p = (JsonObject) o;
            if (id.equals(p.getString("id"))) {
                return p.getMap("statistics");
            }
        }
        throw new AssertionError("No processor " + id + " in " + route);
    }

    private DevConsole console() {
        return PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route");
    }
}
