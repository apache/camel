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

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for {@link MainShutdownStrategy}.
 */
public class SimpleMainShutdownStrategy implements MainShutdownStrategy {
    protected static final Logger LOG = LoggerFactory.getLogger(SimpleMainShutdownStrategy.class);

    private final Set<ShutdownEventListener> listeners = new LinkedHashSet<>();
    private final AtomicBoolean completed;
    private final AtomicBoolean timeoutEnabled = new AtomicBoolean();
    private final AtomicBoolean restarting = new AtomicBoolean();
    private volatile CountDownLatch latch;
    private int extraShutdownTimeout = 15;

    public SimpleMainShutdownStrategy() {
        this.completed = new AtomicBoolean();
        this.latch = new CountDownLatch(1);
    }

    @Override
    public boolean isRunAllowed() {
        return !completed.get();
    }

    @Override
    public void addShutdownListener(ShutdownEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public boolean shutdown() {
        if (completed.compareAndSet(false, true)) {
            LOG.debug("Shutdown called");
            latch.countDown();
            for (ShutdownEventListener l : listeners) {
                try {
                    LOG.trace("ShutdownEventListener: {}", l);
                    l.onShutdown();
                } catch (Exception e) {
                    // ignore as we must continue
                    LOG.debug("Error during ShutdownEventListener: {}. This exception is ignored.", l, e);
                }
            }
            return true;
        }

        return false;
    }

    @Override
    public void await() throws InterruptedException {
        LOG.debug("Await shutdown to complete");
        latch.await();
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        timeoutEnabled.set(true);

        while (true) {
            LOG.debug("Await shutdown to complete with timeout: {} {}", timeout, unit);
            boolean zero = latch.await(timeout, unit);
            if (zero && restarting.compareAndSet(true, false)) {
                // re-create new latch to restart
                LOG.debug("Restarting await shutdown to complete with timeout: {} {}", timeout, unit);
                latch = new CountDownLatch(1);
            } else {
                return zero;
            }
        }
    }

    @Override
    public void restartAwait() {
        // only restart if timeout is in use
        if (timeoutEnabled.get()) {
            LOG.trace("Restarting await with timeout");
            restarting.set(true);
            latch.countDown();
        }
    }

    @Override
    public int getExtraShutdownTimeout() {
        return extraShutdownTimeout;
    }

    @Override
    public void setExtraShutdownTimeout(int extraShutdownTimeout) {
        this.extraShutdownTimeout = extraShutdownTimeout;
    }
}
