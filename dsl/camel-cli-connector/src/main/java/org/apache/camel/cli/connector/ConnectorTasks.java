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
package org.apache.camel.cli.connector;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The periodic tasks of the connector, which must never end: a task that throws is not run again by its scheduler, and
 * these tasks are the only way the Camel CLI reaches the integration (its status, the actions, stop). An Error too: an
 * OutOfMemoryError of a moment while the status is collected would otherwise leave the status as it was then for good,
 * and the integration out of reach of camel stop.
 */
final class ConnectorTasks {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectorTasks.class);
    // an Error is logged as a warning once: the task runs every second, and may fail every time
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private ConnectorTasks() {
    }

    /** Runs the task, and ignores what it throws, so the scheduler runs it again. */
    static void safely(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            // e and not e.getMessage(): the class name says what it was when the message is null (a NullPointerException)
            LOG.debug("Error in Camel CLI connector task due to: {}. This exception is ignored.", e, e);
        } catch (Throwable e) {
            try {
                if (WARNED.compareAndSet(false, true)) {
                    LOG.warn("Error in Camel CLI connector task due to: {}. This error is ignored, and the task runs"
                             + " again.",
                            e, e);
                } else {
                    LOG.debug("Error in Camel CLI connector task due to: {}. This error is ignored.", e, e);
                }
            } catch (Throwable ignored) {
                // logging fails too when the memory is short; the task runs again all the same
            }
        }
    }
}
