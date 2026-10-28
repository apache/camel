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

/**
 * Watches the loop thread of the active run: when it has made no progress for {@link Dst#watchdogMillis()} real
 * milliseconds and is waiting (in a wait of code the harness does not rewrite, for example a latch in Camel core), the
 * watchdog records where it is blocked, prints it, and interrupts the thread, so the seed fails with that report
 * instead of hanging until the surefire timeout.
 */
final class DstWatchdog implements Runnable {

    private static final long PERIOD_MILLIS = 100;
    private static final int STACK_LINES = 15;
    private static Thread thread;

    private DstWatchdog() {
    }

    static synchronized void ensureStarted() {
        if (thread == null) {
            thread = new Thread(new DstWatchdog(), "dst-watchdog");
            thread.setDaemon(true);
            thread.start();
        }
    }

    @Override
    public void run() {
        Dst last = null;
        long lastProgress = -1;
        long since = System.nanoTime();
        while (true) {
            try {
                Thread.sleep(PERIOD_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            Dst dst = Dst.active();
            long now = System.nanoTime();
            if (dst == null || dst != last || dst.progress() != lastProgress) {
                last = dst;
                lastProgress = dst == null ? -1 : dst.progress();
                since = now;
                continue;
            }
            if (dst.isClosed() || dst.stall() != null || (now - since) / 1_000_000 < dst.watchdogMillis()) {
                continue;
            }
            Thread loop = dst.loopThread();
            Thread.State state = loop.getState();
            if (state != Thread.State.WAITING && state != Thread.State.TIMED_WAITING && state != Thread.State.BLOCKED) {
                // running (or done): not a wait
                continue;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("the loop thread has been ").append(state).append(" for ").append((now - since) / 1_000_000)
                    .append(" ms (real time) in a wait the harness does not control; rewrite the class that waits with"
                            + " @DstInstrument(alsoRewrite) or avoid the wait. Stack:");
            StackTraceElement[] stack = loop.getStackTrace();
            for (int i = 0; i < Math.min(STACK_LINES, stack.length); i++) {
                sb.append("\n    at ").append(stack[i]);
            }
            String report = sb.toString();
            dst.stalled(report);
            System.err.println("DST watchdog, seed " + dst.seed() + ": " + report);
            loop.interrupt();
        }
    }
}
