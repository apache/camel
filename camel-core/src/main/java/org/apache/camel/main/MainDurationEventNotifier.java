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
package org.apache.camel.main;

import java.util.EventObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.management.event.ExchangeCompletedEvent;
import org.apache.camel.management.event.ExchangeFailedEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link org.apache.camel.spi.EventNotifier} to trigger shutdown of the Main JVM
 * when maximum number of messages has been processed.
 */
public class MainDurationEventNotifier extends EventNotifierSupport {

    private static final Logger LOG = LoggerFactory.getLogger(MainLifecycleStrategy.class);
    private final CamelContext camelContext;
    private final int maxMessages;
    private final AtomicBoolean completed;
    private final CountDownLatch latch;
    private final boolean stopCamelContext;

    private volatile int doneMessages;

    public MainDurationEventNotifier(CamelContext camelContext, int maxMessages, AtomicBoolean completed, CountDownLatch latch, boolean stopCamelContext) {
        this.camelContext = camelContext;
        this.maxMessages = maxMessages;
        this.completed = completed;
        this.latch = latch;
        this.stopCamelContext = stopCamelContext;
    }

    @Override
    public void notify(EventObject event) throws Exception {
        doneMessages++;

        if (maxMessages > 0 && doneMessages >= maxMessages) {
            if (completed.compareAndSet(false, true)) {
                LOG.info("Duration max messages triggering shutdown of the JVM.");
                // shutting down CamelContext
                if (stopCamelContext) {
                    camelContext.stop();
                }
                // trigger stopping the Main
                latch.countDown();
            }
        }
    }

    @Override
    public boolean isEnabled(EventObject event) {
        return event instanceof ExchangeCompletedEvent || event instanceof ExchangeFailedEvent;
    }

    @Override
    public String toString() {
        return "MainDurationEventNotifier[" + maxMessages + " max messages]";
    }
}
