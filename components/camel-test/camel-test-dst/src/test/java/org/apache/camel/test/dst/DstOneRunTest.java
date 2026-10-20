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

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One run per JVM at a time: the virtual clock is JVM-wide, so a second run while one is open fails fast instead of
 * sharing the clock, and the extension refuses to run when JUnit runs tests in parallel.
 */
class DstOneRunTest {

    @Test
    void aSecondRunWhileOneIsOpenFails() {
        Dst first = new Dst(1);
        try {
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> new Dst(2));
            assertEquals("DST: another run (seed 1) is still active; one run per JVM at a time", e.getMessage());
            assertSame(first, Dst.active(), "the open run stays the active one");
        } finally {
            first.close();
        }
        Dst second = new Dst(2);
        try {
            assertSame(second, Dst.active());
        } finally {
            second.close();
        }
        assertNull(Dst.active());
    }

    @Test
    void theExtensionRefusesParallelTests() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> DstExtension.checkNotParallel(Optional.of("true")));
        assertTrue(e.getMessage().contains(DstExtension.PARALLEL_ENABLED), e.getMessage());
        assertDoesNotThrow(() -> DstExtension.checkNotParallel(Optional.of("false")));
        assertDoesNotThrow(() -> DstExtension.checkNotParallel(Optional.empty()));
    }
}
