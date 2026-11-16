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
package org.apache.camel.dsl.jbang.core.commands;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * quit(0) returns instead of calling System.exit only when no other thread keeps the JVM running.
 */
class CamelJBangMainQuitTest {

    private final Thread current = Thread.currentThread();

    @Test
    void onlyTheCurrentThread() {
        assertThat(CamelJBangMain.hasOtherNonDaemonThread(current, List.of(current))).isFalse();
    }

    @Test
    void aDaemonThreadDoesNotKeepTheJvmRunning() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Thread thread = waitingThread(done, true);
        try {
            assertThat(CamelJBangMain.hasOtherNonDaemonThread(current, List.of(current, thread))).isFalse();
        } finally {
            done.countDown();
            thread.join();
        }
    }

    @Test
    void aNonDaemonThreadKeepsTheJvmRunning() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Thread thread = waitingThread(done, false);
        try {
            assertThat(CamelJBangMain.hasOtherNonDaemonThread(current, List.of(current, thread))).isTrue();
        } finally {
            done.countDown();
            thread.join();
        }
        // ended: no longer keeps the JVM running
        assertThat(CamelJBangMain.hasOtherNonDaemonThread(current, List.of(current, thread))).isFalse();
    }

    @Test
    void aThreadThatIsNotStarted() {
        Thread thread = new Thread(() -> {
        });
        assertThat(CamelJBangMain.hasOtherNonDaemonThread(current, List.of(current, thread))).isFalse();
    }

    private static Thread waitingThread(CountDownLatch done, boolean daemon) {
        Thread thread = new Thread(() -> {
            try {
                done.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        thread.setDaemon(daemon);
        thread.start();
        return thread;
    }
}
