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

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The timeline tells a quiet polling route from one whose scheduler has stopped: it says when the consumer last polled,
 * and since when it polled nothing (CAMEL-26715).
 */
public class TimelineDevConsolePollingTest extends ContextTestSupport {

    private TimelineDevConsole console;

    @Override
    protected boolean useJmx() {
        return true;
    }

    @AfterEach
    void stopConsole() {
        ServiceHelper.stopService(console);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?initialDelay=0&delay=10")).routeId("files").to("mock:result");
                from("direct:orders").routeId("orders").to("mock:orders");
            }
        };
    }

    private static JsonObject route(JsonObject out, String routeId) {
        JsonArray routes = out.getCollection("routes");
        return routes.stream().map(JsonObject.class::cast).filter(r -> routeId.equals(r.getString("routeId")))
                .findFirst().orElse(null);
    }

    @Test
    public void testQuietPollingRoute() {
        console = (TimelineDevConsole) PluginHelper.getDevConsoleResolver(context).resolveDevConsole("timeline");
        console.setCamelContext(context);
        ServiceHelper.startService(console);

        // the seconds are closed by the sampler, once a second
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON);
            JsonObject files = route(out, "files");
            assertThat(files).isNotNull();
            JsonObject polling = files.getMap("polling");
            assertThat(polling).isNotNull();
            assertThat(polling.getLong("lastPollTimestamp")).isNotNull();
            assertThat(polling.getLong("lastPollMessagesTimestamp")).isNull();
        });

        JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON);
        // not a polling route
        JsonObject orders = route(out, "orders");
        assertThat(orders).isNotNull();
        assertThat(orders.containsKey("polling")).isFalse();

        String text = (String) console.call(DevConsole.MediaType.TEXT);
        assertThat(text)
                .containsPattern("files: 0 completed, .* \\(polled nothing since the consumer started, last poll \\S+ ago\\)");
        assertThat(text).doesNotContainPattern("orders: .*polled");
    }

    @Test
    public void testQuietPollingText() {
        long now = 100_000;
        assertThat(TimelineDevConsole.quietPolling(new TimelineDevConsole.Polling(null, null, null), now))
                .isEqualTo("not polled yet");
        assertThat(TimelineDevConsole.quietPolling(new TimelineDevConsole.Polling(now - 500, null, null), now))
                .isEqualTo("polled nothing since the consumer started, last poll 500ms ago");
        assertThat(TimelineDevConsole.quietPolling(new TimelineDevConsole.Polling(now - 500, now - 47_000, 3), now))
                .isEqualTo("polled nothing for 47s, last poll 500ms ago");
    }
}
