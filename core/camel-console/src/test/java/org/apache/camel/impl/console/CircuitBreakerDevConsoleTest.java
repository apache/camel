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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.throttling.ThrottlingExceptionRoutePolicy;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CircuitBreakerDevConsoleTest extends AbstractDevConsoleTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("myRoute")
                        .routePolicy(new ThrottlingExceptionRoutePolicy())
                        .to("mock:result");
                from("direct:other").routeId("otherRoute")
                        .to("mock:other");
            }
        };
    }

    @Test
    public void testCircuitBreakerConsole() {
        DevConsole con = assertConsoleExists("circuit-breaker", "camel");

        JsonObject out = callJson(con);
        JsonArray entries = out.getJsonArray("circuitBreakers");
        assertEquals(1, entries.size());

        JsonObject entry = (JsonObject) entries.get(0);
        assertEquals("myRoute", entry.getString("routeId"));
        assertTrue(entry.getString("state").length() > 0);
        assertEquals(0, entry.getInteger("successfulCalls"));
        assertEquals(0, entry.getInteger("failedCalls"));
    }
}
