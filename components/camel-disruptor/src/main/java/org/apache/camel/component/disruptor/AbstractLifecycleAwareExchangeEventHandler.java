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
package org.apache.camel.component.disruptor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * This abstract base class is used to implement the {@link LifecycleAwareExchangeEventHandler} interface with added
 * support to await starting/stopping by the Disruptor framework.
 */
abstract class AbstractLifecycleAwareExchangeEventHandler implements LifecycleAwareExchangeEventHandler {

    private volatile boolean started;
    private volatile CountDownLatch startedLatch = new CountDownLatch(1);
    private volatile CountDownLatch stoppedLatch = new CountDownLatch(1);

    @Override
    public abstract void onEvent(ExchangeEvent event, long sequence, boolean endOfBatch)
        throws Exception;

    @Override
    public void awaitStarted() throws InterruptedException {
        if (!started) {
            startedLatch.await();
        }
    }

    @Override
    public boolean awaitStarted(final long timeout, final TimeUnit unit) throws InterruptedException {
        return started || startedLatch.await(timeout, unit);
    }

    @Override
    public void awaitStopped() throws InterruptedException {
        if (started) {
            stoppedLatch.await();
        }
    }

    @Override
    public boolean awaitStopped(final long timeout, final TimeUnit unit) throws InterruptedException {
        return !started || stoppedLatch.await(timeout, unit);
    }

    @Override
    public void onStart() {
        stoppedLatch = new CountDownLatch(1);
        startedLatch.countDown();
        started = true;
    }

    @Override
    public void onShutdown() {
        startedLatch = new CountDownLatch(1);
        stoppedLatch.countDown();
        started = false;
    }
}
