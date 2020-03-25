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
package org.apache.camel;

/**
 * Lifecycle API for {@link CamelContext}.
 */
public interface CamelContextLifecycle extends AutoCloseable {

    /**
     * Starts the {@link CamelContext} (<b>important:</b> the start method is not blocked, see more details
     *     <a href="http://camel.apache.org/running-camel-standalone-and-have-it-keep-running.html">here</a>)</li>.
     * <p/>
     * See more details at the class-level javadoc of this class.
     *
     * @throws RuntimeCamelException is thrown if starting failed
     */
    void start();

    /**
     * Stop and shutdown the {@link CamelContext} (will stop all routes/components/endpoints etc and clear internal state/cache).
     * <p/>
     * See more details at the class-level javadoc of this class.
     *
     * @throws RuntimeCamelException is thrown if stopping failed
     */
    void stop();

    /**
     * Whether the CamelContext is started
     *
     * @return true if this CamelContext has been started
     */
    boolean isStarted();

    /**
     * Whether the CamelContext is starting
     *
     * @return true if this CamelContext is being started
     */
    boolean isStarting();

    /**
     * Whether the CamelContext is stopping
     *
     * @return true if this CamelContext is in the process of stopping
     */
    boolean isStopping();

    /**
     * Whether the CamelContext is stopped
     *
     * @return true if this CamelContext is stopped
     */
    boolean isStopped();

    /**
     * Whether the CamelContext is suspending
     *
     * @return true if this CamelContext is in the process of suspending
     */
    boolean isSuspending();

    /**
     * Whether the CamelContext is suspended
     *
     * @return true if this CamelContext is suspended
     */
    boolean isSuspended();

    /**
     * Helper methods so the CamelContext knows if it should keep running.
     * Returns <tt>false</tt> if the CamelContext is being stopped or is stopped.
     *
     * @return <tt>true</tt> if the CamelContext should continue to run.
     */
    boolean isRunAllowed();

    void build();

    void init();

    /**
     * Suspends the CamelContext.
     */
    void suspend();

    /**
     * Resumes the CamelContext.
     */
    void resume();

    /**
     * Shutdown the CamelContext, which means it cannot be started again.
     */
    void shutdown();

    /**
     * Closes (Shutdown) the CamelContext, which means it cannot be started again.
     *
     * @throws Exception is thrown if shutdown failed
     */
    void close() throws Exception;

    /**
     * Get the status of this CamelContext
     *
     * @return the status
     */
    ServiceStatus getStatus();

}
