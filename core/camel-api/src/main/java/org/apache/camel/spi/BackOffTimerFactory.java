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

package org.apache.camel.spi;

import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.util.backoff.BackOffTimer;

/**
 * Factory for creating {@link BackOffTimer}.
 *
 * @see org.apache.camel.util.backoff.BackOff
 */
public interface BackOffTimerFactory {

    /**
     * Creates a new {@link BackOffTimer}.
     *
     * Important: The timer should be started and stopped to control its lifecycle by using
     * {@link org.apache.camel.support.service.ServiceHelper}.
     *
     * @param  name logical name of the timer
     * @return      new empty backoff timer
     */
    BackOffTimer newBackOffTimer(String name);

    /**
     * Creates a new {@link BackOffTimer} using the given executor service.
     *
     * Important: The timer should be started and stopped to control its lifecycle by using
     * {@link org.apache.camel.support.service.ServiceHelper}.
     *
     * @param  name      logical name of the timer
     * @param  scheduler thread pool to use for running tasks
     * @return           new empty backoff timer
     */
    BackOffTimer newBackOffTimer(String name, ScheduledExecutorService scheduler);
}
