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
package org.apache.camel;

/**
 * Various runtime configuration used by {@link org.apache.camel.CamelContext} and {@link org.apache.camel.spi.RouteContext}
 * for cross cutting functions such as tracing, delayer, stream cache and the likes.
 *
 * @version 
 */
public interface RuntimeConfiguration {

    /**
     * Sets whether stream caching is enabled or not (default is disabled).
     * <p/>
     * Is disabled by default
     *
     * @param cache whether stream caching is enabled or not
     */
    void setStreamCaching(Boolean cache);

    /**
     * Returns whether stream cache is enabled
     *
     * @return true if stream cache is enabled
     */
    Boolean isStreamCaching();

    /**
     * Sets whether tracing is enabled or not (default is disabled).
     * <p/>
     * Is disabled by default
     *
     * @param tracing whether tracing is enabled or not.
     */
    void setTracing(Boolean tracing);

    /**
     * Returns whether tracing enabled
     *
     * @return true if tracing is enabled
     */
    Boolean isTracing();

    /**
     * Sets whether handle fault is enabled or not (default is disabled).
     * <p/>
     * Is disabled by default
     *
     * @param handleFault whether handle fault is enabled or not.
     */
    void setHandleFault(Boolean handleFault);

    /**
     * Returns whether tracing enabled
     *
     * @return true if tracing is enabled
     */
    Boolean isHandleFault();

    /**
     * Sets a delay value in millis that a message is delayed at every step it takes in the route path,
     * to slow things down to better helps you to see what goes
     * <p/>
     * Is disabled by default
     *
     * @param delay delay in millis
     */
    void setDelayer(Long delay);

    /**
     * Gets the delay value
     *
     * @return delay in millis, or <tt>null</tt> if disabled
     */
    Long getDelayer();

    /**
     * Sets whether it should automatic start when Camel starts.
     * <p/>
     * Currently only routes can be disabled, as {@link CamelContext} itself are always started}
     * <br/>
     * Default is true to always startup.
     *
     * @param autoStartup  whether to auto startup.
     */
    void setAutoStartup(Boolean autoStartup);

    /**
     * Gets whether it should automatic start when Camel starts.
     *
     * @return true if should auto start
     */
    Boolean isAutoStartup();

    /**
     * Sets the option to use when shutting down routes.
     *
     * @param shutdownRoute the option to use.
     */
    void setShutdownRoute(ShutdownRoute shutdownRoute);

    /**
     * Gets the option to use when shutting down route.
     *
     * @return the option
     */
    ShutdownRoute getShutdownRoute();

    /**
     * Sets the option to use when shutting down a route and how to act when it has running tasks.
     * <p/>
     * A running task is for example a {@link org.apache.camel.BatchConsumer} which has a group
     * of messages to process. With this option you can control whether it should complete the entire
     * group or stop after the current message has been processed.
     *
     * @param shutdownRunningTask the option to use.
     */
    void setShutdownRunningTask(ShutdownRunningTask shutdownRunningTask);

    /**
     * Gets the option to use when shutting down a route and how to act when it has running tasks.
     *
     * @return the option
     */
    ShutdownRunningTask getShutdownRunningTask();

}
