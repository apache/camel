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

import java.time.Instant;

/**
 * The target of the rewritten {@code System.currentTimeMillis()}, {@code System.nanoTime()} and {@code Instant.now()}
 * calls of the code under test (see {@link DstInstrumentation}): the virtual clock of the active run, else the real
 * clock.
 */
public final class DstClock {

    private static final long NANO_OFFSET = 1_000_000_000_000L;

    private DstClock() {
    }

    public static long currentTimeMillis() {
        Dst dst = Dst.active();
        if (dst == null) {
            return System.currentTimeMillis();
        }
        if (!dst.isLoopThread()) {
            DstEscapes.recordRuntime("virtual clock read from a thread outside the harness: "
                                     + Thread.currentThread().getName());
        }
        return dst.currentTimeMillis();
    }

    public static long nanoTime() {
        Dst dst = Dst.active();
        if (dst == null) {
            return System.nanoTime();
        }
        if (!dst.isLoopThread()) {
            DstEscapes.recordRuntime("virtual clock read from a thread outside the harness: "
                                     + Thread.currentThread().getName());
        }
        return NANO_OFFSET + dst.now() * 1_000_000L;
    }

    public static Instant instantNow() {
        Dst dst = Dst.active();
        if (dst == null) {
            return Instant.now();
        }
        if (!dst.isLoopThread()) {
            DstEscapes.recordRuntime("virtual clock read from a thread outside the harness: "
                                     + Thread.currentThread().getName());
        }
        return Instant.ofEpochMilli(dst.currentTimeMillis());
    }
}
