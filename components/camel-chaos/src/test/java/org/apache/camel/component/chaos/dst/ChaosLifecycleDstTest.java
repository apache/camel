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
package org.apache.camel.component.chaos.dst;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstInstrument;
import org.apache.camel.test.dst.DstTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chaos rules in virtual time: a route lifecycle rule and a step rule run against a route in a camel-test-dst world
 * ({@link ChaosLifecycleWorld}). A failing seed is replayed with {@code -Ddst.seed=<seed>}; {@code -Ddst.seeds=5000}
 * runs more seeds.
 * <p/>
 * camel-chaos reads the clock and sleeps only through {@code TimeSource}, and its timed actions run on a scheduled
 * executor of the {@code ExecutorServiceManager}, so it is instrumented here only for the escape scan (strict mode).
 * The route stop and suspend wait for the exchanges in flight in {@code DefaultShutdownStrategy}, whose wait on its
 * shutdown task is rewritten to run the loop.
 */
@DstInstrument(value = "org.apache.camel.component.chaos",
               alsoRewrite = {
                       "org.apache.camel.util.StopWatch", "org.apache.camel.impl.engine.DefaultShutdownStrategy",
                       "org.apache.camel.support.task" })
class ChaosLifecycleDstTest {

    @DstTest(seeds = 200)
    void noMessageIsLostWhileARouteIsDownAndTheRedeliveriesAddUp(Dst dst) throws Exception {
        new ChaosLifecycleWorld(dst).run();
    }

    @Test
    void theSameSeedGivesTheSameReportTimesIncluded() throws Exception {
        for (long seed : List.of(3L, 11L, 42L)) {
            String[] first = runOnce(seed);
            String[] second = runOnce(seed);
            assertThat(second[0]).as("report of seed %d", seed).isEqualTo(first[0]);
            assertThat(second[1]).as("trace of seed %d", seed).isEqualTo(first[1]);
            assertThat(first[0]).contains("\"downAt\"").contains("\"backAt\"");
        }
    }

    @Test
    void aTenMinuteStallTakesMilliseconds() throws Exception {
        Dst dst = new Dst(7);
        try {
            dst.strict(true, List.of());
            ChaosLifecycleWorld world = new ChaosLifecycleWorld(dst, "stall", 600_000L);
            long start = System.nanoTime();
            world.run();
            long real = System.nanoTime() - start;
            assertThat(world.report).contains("\"action\":\"suspend\"").contains("\"duration\":600000")
                    .contains("\"state\":\"back\"");
            assertThat(dst.now()).isGreaterThanOrEqualTo(600_000L);
            // 10 minutes of virtual time: far below a minute of real time also on a busy machine
            assertThat(real).isLessThan(TimeUnit.SECONDS.toNanos(30));
        } finally {
            dst.close();
        }
    }

    /** One run of the world: its report and the hash of its trace. */
    private static String[] runOnce(long seed) throws Exception {
        Dst dst = new Dst(seed);
        try {
            dst.strict(true, List.of());
            ChaosLifecycleWorld world = new ChaosLifecycleWorld(dst);
            world.run();
            return new String[] { world.report, dst.traceHash() };
        } finally {
            dst.close();
        }
    }
}
