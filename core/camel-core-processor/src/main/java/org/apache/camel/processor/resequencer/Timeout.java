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

import java.util.Timer;
import java.util.TimerTask;

/**
 * A timer task that notifies handlers about scheduled timeouts.
 *
 * @see Timer
 * @see TimerTask
 */
public class Timeout extends TimerTask {

    private TimeoutHandler timeoutHandler;

    private final Timer timer;

    private final long timeout;

    /**
     * Creates a new timeout task using the given {@link Timer} instance and timeout value. The task is not scheduled
     * immediately. It will be scheduled by calling this task's {@link #schedule()} method.
     *
     * @param timer   a timer
     * @param timeout a timeout value.
     */
    public Timeout(Timer timer, long timeout) {
        this.timeout = timeout;
        this.timer = timer;
    }

    /**
     * Returns the timeout handler that has been registered for notification.
     *
     * @return the timeout handler.
     */
    public TimeoutHandler getTimeoutHandlers() {
        return timeoutHandler;
    }

    /**
     * Sets a timeout handler for receiving timeout notifications.
     *
     * @param timeoutHandler a timeout handler.
     */
    public void setTimeoutHandler(TimeoutHandler timeoutHandler) {
        this.timeoutHandler = timeoutHandler;
    }

    /**
     * Schedules this timeout task.
     */
    public void schedule() {
        timer.schedule(this, timeout);
    }

    /**
     * Notifies the timeout handler about the scheduled timeout.
     */
    @Override
    public void run() {
        timeoutHandler.timeout(this);
    }

}
