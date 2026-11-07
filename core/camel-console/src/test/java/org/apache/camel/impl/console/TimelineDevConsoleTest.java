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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

public class TimelineDevConsoleTest extends ContextTestSupport {

    private final AtomicInteger stockCalls = new AtomicInteger();
    private TimelineDevConsole console;

    @Override
    protected boolean useJmx() {
        return true;
    }

    @AfterEach
    void stopConsole() {
        ServiceHelper.stopService(console);
    }

    private TimelineDevConsole startConsole() {
        console = (TimelineDevConsole) PluginHelper.getDevConsoleResolver(context).resolveDevConsole("timeline");
        console.setCamelContext(context);
        ServiceHelper.startService(console);
        return console;
    }

    private static long sum(JsonObject o, String key) {
        if (o == null) {
            // the route has no closed second yet
            return -1;
        }
        return o.<List<Number>> getCollection(key).stream().mapToLong(Number::longValue).sum();
    }

    private JsonObject route(JsonObject out, String routeId) {
        JsonArray routes = out.getCollection("routes");
        return routes.stream().map(JsonObject.class::cast).filter(r -> routeId.equals(r.getString("routeId")))
                .findFirst().orElse(null);
    }

    @Test
    public void theConsoleRecordsWhatTheRoutesDidPerSecond() {
        startConsole();

        template.sendBody("direct:orders", "a");
        template.sendBody("direct:orders", "b");
        template.send("direct:failing", e -> e.getMessage().setBody("c"));

        // the seconds are closed by the sampler, once a second
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON);
            JsonObject orders = route(out, "orders");
            assertThat(orders).isNotNull();
            assertThat(sum(orders, "completed") + sum(orders, "recovered")).isEqualTo(2);
            assertThat(sum(route(out, "failing"), "failed")).isEqualTo(1);
        });

        JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON);
        assertThat(out.getInteger("seconds")).isEqualTo(300);
        JsonObject orders = route(out, "orders");
        assertThat(orders.getString("from")).isEqualTo("direct://orders");
        assertThat(orders.<List<Number>> getCollection("completed")).hasSize(300);

        JsonArray errors = out.getCollection("errors");
        assertThat(errors).anySatisfy(e -> {
            JsonObject err = (JsonObject) e;
            assertThat(err.getString("routeId")).isEqualTo("failing");
            assertThat(err.getString("exception")).isEqualTo("java.lang.IllegalArgumentException");
        });

        JsonArray exchanges = out.getCollection("exchanges");
        assertThat(exchanges).hasSize(3);
        assertThat(exchanges).anySatisfy(e -> {
            JsonObject ex = (JsonObject) e;
            assertThat(ex.getString("routeId")).isEqualTo("failing");
            assertThat(ex.getBoolean("failed")).isTrue();
            assertThat(ex.getString("exception")).isEqualTo("Forced");
        });
    }

    @Test
    public void anExchangeRecoveredByARedeliveryCountsAlsoWhenTheRouteRemovesTheCamelHeaders() {
        startConsole();

        template.sendBody("direct:orders", "a");

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            JsonObject orders = route((JsonObject) console.call(DevConsole.MediaType.JSON), "orders");
            assertThat(orders).isNotNull();
            assertThat(sum(orders, "recovered")).isEqualTo(1);
        });
        JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON);
        JsonObject orders = route(out, "orders");
        assertThat(sum(orders, "redeliveryAttempts")).isEqualTo(1);
        assertThat(sum(orders, "completed")).isZero();
        JsonObject ex = (JsonObject) out.<JsonArray> getCollection("exchanges").get(0);
        assertThat(ex.getBoolean("redelivered")).isTrue();
    }

    @Test
    public void theSecondsAndTheRoutesCanBeAskedFor() {
        startConsole();

        template.send("direct:failing", e -> e.getMessage().setBody("c"));
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(
                sum(route((JsonObject) console.call(DevConsole.MediaType.JSON), "failing"), "failed")).isEqualTo(1));

        JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON,
                Map.of(TimelineDevConsole.SECONDS, "10", TimelineDevConsole.ROUTE_ID, "fail*",
                        TimelineDevConsole.EXCHANGES, "false"));
        assertThat(out.getInteger("seconds")).isEqualTo(10);
        assertThat(out.<JsonArray> getCollection("routes")).singleElement()
                .satisfies(r -> assertThat(((JsonObject) r).getString("routeId")).isEqualTo("failing"));
        assertThat(out.containsKey("exchanges")).isFalse();
        assertThat(out.<List<Number>> getCollection("exchangesDone")).hasSize(10);

        String text = (String) console.call(DevConsole.MediaType.TEXT);
        assertThat(text).contains("failing: 0 completed, 0 recovered, 0 handled, 1 failed");
    }

    @Test
    public void thePartsOfASplitAreNotKeptAsExchangesOfTheRoute() {
        startConsole();

        template.sendBody("direct:batch", "a,b,c");
        template.sendBody("direct:batch", "d,e");

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON);
            assertThat(sum(route(out, "batch"), "completed")).isEqualTo(2);
            assertThat(out.<JsonArray> getCollection("exchanges")).hasSize(2);
            assertThat(out.<List<Number>> getCollection("exchangesDone").stream().mapToLong(Number::longValue).sum())
                    .isEqualTo(2);
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class).maximumRedeliveries(2).redeliveryDelay(0);

                from("direct:orders").routeId("orders")
                        .process(e -> {
                            // the first call fails, the redelivery succeeds
                            if (stockCalls.incrementAndGet() % 2 == 1) {
                                throw new IllegalStateException("stock down");
                            }
                        })
                        .removeHeaders("Camel*");

                from("direct:batch").routeId("batch")
                        .split(body().tokenize(",")).to("mock:part").end();

                from("direct:failing").routeId("failing")
                        .throwException(new IllegalArgumentException("Forced"));
            }
        };
    }
}
