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
package org.apache.camel.main;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.RuntimeCamelException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A failure to start that was logged is not printed a second time by the JVM, also when it is wrapped.
 */
@Isolated
class LoggedStartupFailureTest {

    private final List<Throwable> printed = new CopyOnWriteArrayList<>();
    private Thread.UncaughtExceptionHandler original;

    @BeforeEach
    void recordWhatTheThreadPrints() {
        original = Thread.currentThread().getUncaughtExceptionHandler();
        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> printed.add(e));
    }

    @AfterEach
    void restoreTheHandler() {
        // the thread group is what the thread uses without a handler of its own
        Thread.currentThread().setUncaughtExceptionHandler(original instanceof ThreadGroup ? null : original);
    }

    private void leavesTheThread(Throwable e) {
        Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
    }

    @Test
    void theLoggedFailureIsNotPrintedAgain() {
        Exception failure = new IllegalStateException("cannot start");
        LoggedStartupFailure.skipWhenUncaught(failure);

        leavesTheThread(failure);
        // wrapped as BaseService.doFail does, and as the launcher of a fat jar does, which calls main by reflection
        leavesTheThread(new RuntimeCamelException(failure));
        leavesTheThread(new InvocationTargetException(new RuntimeCamelException(failure)));

        assertThat(printed).isEmpty();
    }

    @Test
    void anotherExceptionIsPrintedAsBefore() {
        LoggedStartupFailure.skipWhenUncaught(new IllegalStateException("cannot start"));
        IllegalArgumentException other = new IllegalArgumentException("another");

        leavesTheThread(other);

        assertThat(printed).containsExactly(other);
    }

    @Test
    void aLaterFailureReplacesTheOneBefore() {
        Exception first = new IllegalStateException("first");
        Exception second = new IllegalStateException("second");
        LoggedStartupFailure.skipWhenUncaught(first);
        LoggedStartupFailure.skipWhenUncaught(second);

        leavesTheThread(first);
        leavesTheThread(second);

        assertThat(printed).containsExactly(first);
    }
}
