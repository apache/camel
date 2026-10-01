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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.model.SwitchDefinition;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwitchRouteStructureDevConsoleTest extends ContextTestSupport {
    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void labelsIdentifyCaseAndFallbackDestinations(boolean brief) {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-structure");
        String text = (String) console.call(DevConsole.MediaType.TEXT, Map.of("filter", "tickets", "brief", brief));
        assertTrue(text.contains("case[billing -> direct:billing]"), text);
        assertTrue(text.contains("case[technical -> direct:technical]"), text);
        assertTrue(text.contains("otherwise[direct:review]"), text);

        JsonObject json = (JsonObject) console.call(DevConsole.MediaType.JSON, Map.of("filter", "tickets", "brief", brief));
        JsonObject route = (JsonObject) json.getCollection("routes").iterator().next();
        JsonObject fallback = route.getCollection("code").stream().map(JsonObject.class::cast)
                .filter(line -> "dispatch-otherwise".equals(line.getString("id"))).findFirst().orElseThrow();
        assertEquals("to", fallback.getString("type"));
        assertEquals("otherwise[direct:review]", fallback.getString("code"));
        assertEquals("direct:review", fallback.getString("uri"));
    }

    @Test
    void labelsMaskSecretsAndBriefModeKeepsTheFallbackIdentity() {
        SwitchDefinition sw = (SwitchDefinition) context.getRouteDefinition("tickets").getOutputs().get(0);
        sw.getCases().get(0).setUri("direct:billing?password=caseSecret");
        sw.getOtherwise().setUri("direct:review?password=fallbackSecret");
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-structure");
        String text = (String) console.call(DevConsole.MediaType.TEXT, Map.of("filter", "tickets"));
        assertTrue(text.contains("case[billing -> direct:billing?password="), text);
        assertTrue(text.contains("otherwise[direct:review?password="), text);
        assertFalse(text.contains("caseSecret"), text);
        assertFalse(text.contains("fallbackSecret"), text);

        String brief = (String) console.call(DevConsole.MediaType.TEXT, Map.of("filter", "tickets", "brief", true));
        assertTrue(brief.contains("case[billing -> direct:billing]"), brief);
        assertTrue(brief.contains("otherwise[direct:review]"), brief);
        assertFalse(brief.contains("password"), brief);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:tickets").routeId("tickets")
                        .doSwitch(header("department")).id("dispatch")
                            .doCase("billing", "direct:billing")
                            .doCase("technical").to("direct:technical")
                            .otherwise("direct:review")
                        .end();
                from("direct:billing").to("mock:billing");
                from("direct:technical").to("mock:technical");
                from("direct:review").to("mock:review");
            }
        };
    }
}
