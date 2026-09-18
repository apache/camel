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
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EventConsoleTest extends AbstractDevConsoleTest {

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
    public void testEventConsole() {
        DevConsole con = assertConsoleExists("event", "camel");
        // EventConsole allocates its ring buffers and subscribes to events in doInit()/doStart(),
        // normally triggered by DevConsoleRegistry.register() - start it explicitly since this
        // test resolves it directly, then generate an exchange event for it to capture
        ServiceHelper.startService(con);
        template.sendBody("direct:start", "hello");

        JsonObject out = callJson(con);
        JsonArray exchangeEvents = out.getJsonArray("exchangeEvents");
        assertNotNull(exchangeEvents);
        assertTrue(exchangeEvents.size() > 0);

        JsonObject first = (JsonObject) exchangeEvents.get(0);
        assertNotNull(first.getString("type"));
        assertNotNull(first.getString("exchangeId"));
        assertNotNull(first.getString("message"));
    }
}
