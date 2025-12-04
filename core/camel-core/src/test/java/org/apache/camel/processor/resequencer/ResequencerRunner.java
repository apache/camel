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

package org.apache.camel.processor.resequencer;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResequencerRunner<E> extends Thread {
    private static final Logger LOG = LoggerFactory.getLogger(ResequencerRunner.class);

    private final Lock lock = new ReentrantLock();
    private final ResequencerEngineSync<E> resequencer;

    private final long interval;

    private boolean cancelRequested;

    private volatile boolean running;

    public ResequencerRunner(ResequencerEngineSync<E> resequencer, long interval) {
        this.resequencer = resequencer;
        this.interval = interval;
        this.cancelRequested = false;
    }

    @Override
    public void run() {
        while (!cancelRequested()) {
            running = true;
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                LOG.info("The test execution was interrupted", e);
            }
            try {
                resequencer.deliver();
            } catch (Exception e) {
                LOG.info("The test execution was interrupted", e);
            }
        }
        super.run();
        running = false;
    }

    public void cancel() {
        lock.lock();
        try {
            this.cancelRequested = true;
        } finally {
            lock.unlock();
        }
    }

    private boolean cancelRequested() {
        lock.lock();
        try {
            return cancelRequested;
        } finally {
            lock.unlock();
        }
    }

    public boolean isRunning() {
        return running;
    }
}
