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
package org.apache.camel.cli.connector;

import java.net.ConnectException;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.console.ErrorRegistryConsole;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The status file says how many errors happened, where the error registry keeps only a few entries of each kind.
 */
class ErrorCountsTest {

    @Test
    void theErrorsThatHappenedNotTheEntriesKept() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.getErrorRegistry().setEnabled(true);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:supplier").routeId("supplier").throwException(new ConnectException("unreachable"));
                    from("direct:stock").routeId("stock").throwException(new IllegalStateException("no stock"));
                }
            });
            context.start();
            long before = System.currentTimeMillis();
            for (int i = 0; i < 10; i++) {
                send(context, "direct:supplier");
            }
            for (int i = 0; i < 2; i++) {
                send(context, "direct:stock");
            }

            ErrorRegistryConsole console = new ErrorRegistryConsole();
            console.setCamelContext(context);
            @SuppressWarnings("unchecked")
            Map<String, Object> json = (Map<String, Object>) console.call(DevConsole.MediaType.JSON);
            JsonObject summary = new JsonObject();
            ErrorCounts.summarize(json, summary);

            // the registry kept 3 of the 10 and both of the 2
            assertThat(json.get("size")).isEqualTo(5);
            assertThat(summary.getLong("total")).isEqualTo(12);
            List<?> kinds = (List<?>) summary.get("kinds");
            assertThat(kinds).hasSize(2);
            JsonObject first = (JsonObject) kinds.get(0);
            assertThat(first.getString("routeId")).isEqualTo("supplier");
            assertThat(first.getString("exceptionType")).isEqualTo("java.net.ConnectException");
            assertThat(first.getLong("count")).isEqualTo(10);
            assertThat(((JsonObject) kinds.get(1)).getLong("count")).isEqualTo(2);
            // when the newest error happened: the last of the 2 sent to the stock route
            assertThat(summary.getLong("lastTimestamp")).isBetween(before, System.currentTimeMillis());
        }
    }

    @Test
    void noErrors() {
        JsonObject summary = new JsonObject();
        ErrorCounts.summarize(Map.of("size", 0), summary);
        assertThat(summary.getLong("total")).isZero();
        assertThat((List<?>) summary.get("kinds")).isEmpty();
        assertThat(summary.containsKey("lastTimestamp")).isFalse();
    }

    private static void send(CamelContext context, String uri) {
        try {
            context.createProducerTemplate().sendBody(uri, "x");
        } catch (Exception e) {
            // expected: the route fails
        }
    }
}
