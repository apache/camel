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
package org.apache.camel.component.chaos;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rules added, changed and removed while many threads send: every send sees one snapshot of the rules, so each send is
 * failed, dropped or delivered exactly once, and the counts of the rules add up to what the senders saw.
 */
class ChaosStressTest extends ChaosTestSupport {

    private static final int SENDERS = 8;
    private static final int SENDS = 2_000;
    private static final int CYCLES = 30;

    private final AtomicLong delivered = new AtomicLong();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("start").to("direct:sink");
                from("direct:sink").routeId("sink").process(e -> delivered.incrementAndGet());
            }
        };
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void countsAddUpWhileRulesChange() throws Exception {
        AtomicLong sent = new AtomicLong();
        AtomicLong failed = new AtomicLong();
        AtomicLong unexpected = new AtomicLong();
        AtomicBoolean sending = new AtomicBoolean(true);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(SENDERS + 1);
        List<Future<?>> senders = new ArrayList<>();
        try {
            for (int t = 0; t < SENDERS; t++) {
                senders.add(pool.submit(() -> {
                    go.await();
                    for (int i = 0; i < SENDS; i++) {
                        Exchange out = template.send("direct:start", e -> e.getMessage().setBody("x"));
                        if (out.getException() instanceof ChaosException) {
                            failed.incrementAndGet();
                        } else if (out.getException() != null) {
                            unexpected.incrementAndGet();
                        }
                        sent.incrementAndGet();
                    }
                    return null;
                }));
            }
            Future<?> mutator = pool.submit(() -> {
                go.await();
                for (int c = 0; c < CYCLES && sending.get(); c++) {
                    String id = chaos().addRule("endpoint=direct:sink error");
                    awaitSends(sent, sending, 20);
                    chaos().changeRule(id, "endpoint=direct:sink drop");
                    awaitSends(sent, sending, 20);
                    chaos().removeRule(id);
                    awaitSends(sent, sending, 20);
                }
                return null;
            });
            go.countDown();
            for (Future<?> f : senders) {
                f.get(100, TimeUnit.SECONDS);
            }
            sending.set(false);
            mutator.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        long errorsFired = 0;
        long dropsFired = 0;
        long matches = 0;
        JsonArray rules = (JsonArray) chaos().report().toJson().get("rules");
        for (Object o : rules) {
            JsonObject rule = (JsonObject) o;
            long fired = ((JsonObject) rule.get("fired")).getLong("send");
            if (rule.getString("rule").endsWith("error")) {
                errorsFired += fired;
            } else {
                dropsFired += fired;
            }
            matches += rule.getLong("matches");
        }
        assertThat(unexpected.get()).isZero();
        assertThat(sent.get()).isEqualTo((long) SENDERS * SENDS);
        // each send was failed, dropped or delivered, once
        assertThat(errorsFired).isEqualTo(failed.get());
        assertThat(failed.get() + dropsFired + delivered.get()).isEqualTo(sent.get());
        // without a rate every match fires
        assertThat(matches).isEqualTo(errorsFired + dropsFired);
        assertThat(errorsFired).isPositive();
        assertThat(dropsFired).isPositive();
    }

    /** Waits (spinning, no sleep) until the senders sent some more, or stopped. */
    private static void awaitSends(AtomicLong sent, AtomicBoolean sending, int more) {
        long target = sent.get() + more;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (sent.get() < target && sending.get() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }
}
