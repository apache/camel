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

/**
 * Graceful shutdown when using Camel Main.
 */
public interface MainShutdownStrategy {

    /**
     * Event listener when shutting down.
     */
    interface ShutdownEventListener {

        /**
         * Callback on shutdown
         */
        void onShutdown();

    }

    void addShutdownListener(ShutdownEventListener listener);

    /**
     * @return true if the application is allowed to run.
     */
    boolean isRunAllowed();

    /**
     * @return true if the shutdown has been initiated by the caller.
     */
    boolean shutdown();

    /**
     * Wait for main to complete.
     */
    void await() throws InterruptedException;

    /**
     * Wait for main to complete.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the {@code timeout} argument
     */
    void await(long timeout, TimeUnit unit) throws InterruptedException;
}
