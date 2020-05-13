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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleMainShutdownStrategy implements MainShutdownStrategy {
    protected static final Logger LOG = LoggerFactory.getLogger(SimpleMainShutdownStrategy.class);

    private final AtomicBoolean completed;
    private final CountDownLatch latch;

    public SimpleMainShutdownStrategy() {
        this.completed = new AtomicBoolean(false);
        this.latch = new CountDownLatch(1);
    }

    @Override
    public boolean isRunAllowed() {
        return !completed.get();
    }

    @Override
    public boolean shutdown() {
        if (completed.compareAndSet(false, true)) {
            latch.countDown();
            return true;
        }

        return false;
    }

    @Override
    public void await() throws InterruptedException {
        latch.await();
    }

    @Override
    public void await(long timeout, TimeUnit unit) throws InterruptedException {
        latch.await(timeout, unit);
    }
}
