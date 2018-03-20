/**
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

package org.apache.camel.test.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestRunnable implements Runnable {
    public static final long SLEEP_MILLIS = 1000;
    final int id;
    final int runCount;

    Logger log = LoggerFactory.getLogger(this.getClass());
    boolean started;

    public TestRunnable(int id, int runCount) {
        this.id = id;
        this.runCount = runCount;
    }

    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();
        log.info("{}: Runnable {} starting", threadName, id);
        int currentRun = 0;
        started = true;
        try {
            while (started && ++currentRun <= runCount) {
                Thread.sleep(SLEEP_MILLIS);
                log.info("{}: Runnable {} running {} of {} runs", threadName, id, currentRun, runCount);
            }
        } catch (InterruptedException e) {
            log.info("{}: Runnable {} interrupted on run {}", threadName, id, currentRun);
        } finally {
            log.info("{}: Runnable {} exiting after {} runs", threadName, id, currentRun - 1);
        }
    }

    public void stop() {
        started = false;
    }

    public String status() {
        return String.format("Runnable %d is %s", id, started ? "started" : "stopped");
    }
}
