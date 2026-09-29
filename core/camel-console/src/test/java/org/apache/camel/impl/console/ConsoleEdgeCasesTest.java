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

import java.util.List;
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
 * Consoles used without JMX (not enabled by ContextTestSupport).
 */
public class ConsoleEdgeCasesTest extends ContextTestSupport {

    private DevConsole console(String id) {
        return PluginHelper.getDevConsoleResolver(context).resolveDevConsole(id);
    }

    @Test
    public void testTraceConsoleDumpFalse() {
        context.setBacklogTracing(true);
        JsonObject out = (JsonObject) console("trace").call(DevConsole.MediaType.JSON, Map.of("dump", "false"));
        // not dumping, so the status of the tracer
        assertThat(out.get("traces")).isNull();
    }

    @Test
    public void testBrowseConsoleFreshSizeWithLimit() {
        for (int i = 0; i < 20; i++) {
            template.sendBody("direct:start", "Message " + i);
        }
        JsonObject out = (JsonObject) console("browse").call(DevConsole.MediaType.JSON,
                Map.of("freshSize", "true", "limit", "5", "filter", "seda*"));
        JsonArray browse = out.getCollection("browse");
        JsonObject entry = (JsonObject) browse.get(0);
        assertThat(entry.getInteger("queueSize")).isEqualTo(20);
        List<?> messages = entry.getCollection("messages");
        assertThat(messages).hasSize(5);

        String text = (String) console("browse").call(DevConsole.MediaType.TEXT,
                Map.of("freshSize", "true", "limit", "5", "filter", "seda*"));
        assertThat(text).contains("Message 4").doesNotContain("Message 5");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setBacklogTracingStandby(true);

                from("direct:start").to("seda:queue1");
            }
        };
    }
}
