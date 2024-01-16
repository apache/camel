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

import org.apache.camel.CamelContext;
import org.apache.camel.spi.CamelContextTracker;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.concurrent.ThreadHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link MainShutdownStrategy} that add a virtual machine shutdown hook to properly stop the main
 * instance.
 */
public class DefaultMainShutdownStrategy extends SimpleMainShutdownStrategy {
    protected static final Logger LOG = LoggerFactory.getLogger(DefaultMainShutdownStrategy.class);

    private final AtomicBoolean hangupIntercepted;
    private final BaseMainSupport main;

    private volatile boolean hangupInterceptorEnabled;

    public DefaultMainShutdownStrategy(BaseMainSupport main) {
        this.main = main;
        this.hangupIntercepted = new AtomicBoolean();
    }

    /**
     * Disable the hangup support. No graceful stop by calling stop() on a Hangup signal.
     */
    public void disableHangupSupport() {
        hangupInterceptorEnabled = false;
    }

    /**
     * Hangup support is enabled by default.
     */
    public void enableHangupSupport() {
        hangupInterceptorEnabled = true;
    }

    @Override
    public void await() throws InterruptedException {
        installHangupInterceptor();
        super.await();
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        installHangupInterceptor();
        return super.await(timeout, unit);
    }

    private void handleHangup() {
        LOG.debug("Received hangup signal, stopping the main instance.");
        // and shutdown listener to allow camel context to graceful shutdown if JVM shutdown hook is triggered
        // as otherwise the JVM terminates before Camel is graceful shutdown
        addShutdownListener(() -> {
            LOG.trace("OnShutdown");
            // attempt to wait for main to complete its shutdown of camel context
            if (main.getCamelContext() != null) {
                final CountDownLatch latch = new CountDownLatch(1);
                // use tracker to know when camel context is destroyed so we can complete this listener quickly
                CamelContextTracker tracker = new CamelContextTracker() {
                    @Override
                    public void contextDestroyed(CamelContext camelContext) {
                        latch.countDown();
                    }
                };
                tracker.open();

                // use timeout from camel shutdown strategy and add extra to allow camel to shut down graceful
                long max = TimeUnit.SECONDS.toMillis(getExtraShutdownTimeout())
                           + main.getCamelContext().getShutdownStrategy().getTimeUnit()
                                   .toMillis(main.getCamelContext().getShutdownStrategy().getTimeout());
                int waits = 0;
                boolean done = false;
                StopWatch watch = new StopWatch();
                while (!main.getCamelContext().isStopped() && !done && watch.taken() < max) {
                    String msg = "Waiting for CamelContext to graceful shutdown (max:"
                                 + TimeUtils.printDuration(max, true) + ", elapsed:"
                                 + TimeUtils.printDuration(watch.taken(), true) + ")";
                    if (waits > 0 && waits % 5 == 0) {
                        // do some info logging every 5th time
                        LOG.info(msg);
                    } else {
                        LOG.trace(msg);
                    }
                    waits++;
                    try {
                        // wait 1 sec and loop and log activity, so we can see we are waiting
                        done = latch.await(1000, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                boolean success = done || main.getCamelContext().isStopped();
                if (!success) {
                    LOG.warn("CamelContext not yet shutdown completely after: {}. Forcing shutdown.",
                            TimeUtils.printDuration(watch.taken(), true));
                }
                tracker.close();
            }
            LOG.trace("OnShutdown complete");
        });
        shutdown();
    }

    private void installHangupInterceptor() {
        if (this.hangupIntercepted.compareAndSet(false, hangupInterceptorEnabled)) {
            Thread task = new Thread(this::handleHangup);
            task.setName(ThreadHelper.resolveThreadName(null, "CamelHangupInterceptor"));

            Runtime.getRuntime().addShutdownHook(task);
        }
    }
}
