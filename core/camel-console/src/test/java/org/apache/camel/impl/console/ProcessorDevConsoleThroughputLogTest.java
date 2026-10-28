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
 * A to(log) with groupInterval or groupSize is managed as a throughput logger, which the processor and route consoles
 * must report like any other to.
 */
public class ProcessorDevConsoleThroughputLogTest extends ContextTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    private DevConsole console(String id) {
        return PluginHelper.getDevConsoleResolver(context).resolveDevConsole(id);
    }

    @Test
    public void testProcessorConsoleJson() {
        JsonObject out = (JsonObject) console("processor").call(DevConsole.MediaType.JSON);
        JsonArray processors = out.getCollection("processors");
        assertThat(processors).hasSize(3);
        assertThat(destination(processors, "plain-log")).isEqualTo("log://plain");
        assertThat(destination(processors, "interval-log")).isEqualTo("log://interval?groupInterval=5000");
        assertThat(destination(processors, "size-log")).isEqualTo("log://size?groupSize=10");
    }

    @Test
    public void testProcessorConsoleText() {
        String out = (String) console("processor").call(DevConsole.MediaType.TEXT);
        assertThat(out).contains("Uri: log://interval?groupInterval=5000", "Uri: log://size?groupSize=10");
    }

    @Test
    public void testRouteConsoleWithProcessors() {
        JsonObject out = (JsonObject) console("route").call(DevConsole.MediaType.JSON, Map.of("processors", "true"));
        JsonArray routes = out.getCollection("routes");
        JsonArray processors = ((JsonObject) routes.get(0)).getCollection("processors");
        assertThat(destination(processors, "interval-log")).isEqualTo("log://interval?groupInterval=5000");
    }

    private static String destination(JsonArray processors, String id) {
        JsonObject processor = (JsonObject) processors.stream()
                .filter(p -> id.equals(((JsonObject) p).getString("id"))).findFirst().orElseThrow();
        return processor.getString("uri");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("throughput")
                        .to("log:plain").id("plain-log")
                        .to("log:interval?groupInterval=5000").id("interval-log")
                        .to("log:size?groupSize=10").id("size-log");
            }
        };
    }
}
