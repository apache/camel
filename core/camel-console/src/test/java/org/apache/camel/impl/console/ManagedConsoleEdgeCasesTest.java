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
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consoles driven by camel-management (JMX enabled).
 */
public class ManagedConsoleEdgeCasesTest extends ContextTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    private DevConsole console(String id) {
        return PluginHelper.getDevConsoleResolver(context).resolveDevConsole(id);
    }

    @Test
    public void testConsumerConsoleScheduledPollConsumer() {
        JsonObject out = (JsonObject) console("consumer").call(DevConsole.MediaType.JSON);
        JsonArray consumers = out.getCollection("consumers");
        JsonObject file = (JsonObject) consumers.stream()
                .filter(c -> ((JsonObject) c).getString("uri").startsWith("file:")).findFirst().orElseThrow();
        assertThat(file.getBoolean("scheduled")).isTrue();
        assertThat(file.getLong("delay")).isEqualTo(5000L);

        String text = (String) console("consumer").call(DevConsole.MediaType.TEXT);
        assertThat(text).contains("Polling:");
    }

    @Test
    public void testProcessorConsoleLimit() {
        JsonObject out = (JsonObject) console("processor").call(DevConsole.MediaType.JSON, Map.of("limit", "1"));
        JsonArray processors = out.getCollection("processors");
        assertThat(processors).hasSize(1);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("file:target/data/console-edge?delay=5000&initialDelay=600000").routeId("files")
                        .to("log:a").to("log:b").to("log:c");
            }
        };
    }
}
