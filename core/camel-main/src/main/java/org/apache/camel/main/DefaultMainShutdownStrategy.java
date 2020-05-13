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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.util.concurrent.ThreadHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link MainShutdownStrategy} that add a virtual machine shutdown hook to
 * properly stop the main instance.
 */
public class DefaultMainShutdownStrategy extends SimpleMainShutdownStrategy {
    protected static final Logger LOG = LoggerFactory.getLogger(DefaultMainShutdownStrategy.class);

    private final AtomicBoolean hangupIntercepted;

    private volatile boolean hangupInterceptorEnabled;

    public DefaultMainShutdownStrategy() {
        this.hangupIntercepted = new AtomicBoolean();
    }

    /**
     * Disable the hangup support. No graceful stop by calling stop() on a
     * Hangup signal.
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
    public void await(long timeout, TimeUnit unit) throws InterruptedException {
        installHangupInterceptor();
        super.await(timeout, unit);
    }

    private void handleHangup() {
        LOG.info("Received hang up - stopping the main instance.");
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
