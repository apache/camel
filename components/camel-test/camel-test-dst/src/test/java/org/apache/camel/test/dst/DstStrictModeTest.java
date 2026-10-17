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
package org.apache.camel.test.dst;

import java.util.List;

import org.apache.camel.test.dst.sample.SampleEscapes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Strict mode: reaching an escape point the harness does not control fails the seed, also when the code under test
 * swallows the violation.
 */
@DstInstrument("org.apache.camel.test.dst.sample")
class DstStrictModeTest {

    @Test
    void reachingAnEscapeFailsTheSeed() {
        Dst dst = new Dst(1);
        try {
            dst.strict(true, List.of());
            DstViolation e = assertThrows(DstViolation.class, SampleEscapes::ownThread);
            assertEquals("escape", e.getInvariant());
            assertTrue(e.getMessage().contains("SampleEscapes.ownThread"), e.getMessage());
            assertTrue(e.getMessage().contains("new Thread"), e.getMessage());
        } finally {
            dst.close();
        }
    }

    @Test
    void aSwallowedEscapeStillFailsTheSeed() {
        Dst dst = new Dst(1);
        try {
            dst.strict(true, List.of());
            DstExecutor ex = dst.executor("ex", 1);
            // the task fails with the violation, which nobody looks at
            ex.execute(SampleEscapes::ownThread);
            ex.schedule("later", 1_000, () -> {
            });
            DstViolation e = assertThrows(DstViolation.class, dst::runUntilQuiet);
            assertEquals("escape", e.getInvariant());
            assertNotNull(dst.pendingFailure(), "the extension fails the seed at the end as well");
        } finally {
            dst.close();
        }
    }

    @Test
    void escapesFromAnotherThreadAreReportedOnTheLoop() throws Exception {
        Dst dst = new Dst(1);
        try {
            dst.strict(true, List.of());
            DstExecutor ex = dst.executor("ex", 1);
            // a thread outside the harness submits a task
            Thread outside = new Thread(() -> ex.execute(() -> {
            }), "outside");
            outside.start();
            outside.join();
            assertTrue(dst.escape().contains("task submitted from a thread outside the harness"), dst.escape());
            assertThrows(DstViolation.class, dst::runUntilQuiet);
        } finally {
            dst.close();
        }
    }

    @Test
    void allowedEscapesAndNonStrictRunsOnlyRecord() {
        Dst dst = new Dst(1);
        try {
            dst.strict(true, List.of("SampleEscapes.ownThread"));
            SampleEscapes.ownThread();
            assertNull(dst.escape());
            dst.strict(false, List.of());
            SampleEscapes.commonPool(() -> {
            }).join();
            assertNull(dst.escape());
            assertTrue(DstEscapes.runtime().stream().anyMatch(l -> l.contains("SampleEscapes.commonPool")),
                    DstEscapes.runtime().toString());
        } finally {
            dst.close();
        }
    }
}
