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
package org.apache.camel.test.dst.demo.eventhubs;

import java.util.Map;

import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstInstrument;
import org.apache.camel.test.dst.DstTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Demo 1: the checkpointing of camel-azure-eventhubs (CAMEL-25683, apache/camel#27922) under seeded event bursts,
 * downstream latency and failures, and slow or failing checkpoint store answers.
 * <p>
 * Runs against the code of the camel-azure-eventhubs module of the reactor. Enabled with {@code -Ddst.demo=true}: on a
 * branch without the fix it fails by design.
 */
@EnabledIfSystemProperty(named = "dst.demo", matches = "true")
@DstInstrument("org.apache.camel.component.azure.eventhubs")
class EventHubsCheckpointDstTest {

    @DstTest(seeds = 2000)
    void everyCompletedEventIsCheckpointedWhenThePartitionGoesQuiet(Dst dst) throws Exception {
        new EventHubsWorld(dst).run();
    }

    /** The same seed twice in this JVM: identical step traces (the cross-JVM check compares the trace files). */
    @Test
    void replayOfASeedGivesTheSameTrace() throws Exception {
        long seed = Long.getLong("dst.replayCheckSeed", 1);
        assertEquals(traceOf(seed), traceOf(seed));
    }

    private static String traceOf(long seed) throws Exception {
        Dst dst = new Dst(seed);
        try {
            try {
                new EventHubsWorld(dst, false).run();
            } catch (AssertionError e) {
                dst.trace("FAILED " + e);
            }
            return dst.traceHash() + " " + dst.traceLines().size();
        } finally {
            dst.close();
        }
    }

    @AfterAll
    static void printProbes() {
        synchronized (EventHubsWorld.PROBES) {
            StringBuilder sb = new StringBuilder("DST eventhubs probes (seeds in which each cause was seen):\n");
            for (Map.Entry<String, Integer> e : EventHubsWorld.PROBES.entrySet()) {
                sb.append("  ").append(e.getValue()).append(" seeds: ").append(e.getKey()).append("  first seeds ")
                        .append(EventHubsWorld.PROBE_SEEDS.get(e.getKey()).stream().limit(10).toList()).append('\n');
            }
            System.out.print(sb);
        }
    }
}
