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
 * Various runtime configuration options used by {@link org.apache.camel.CamelContext} and {@link org.apache.camel.spi.RouteContext}
 * for cross cutting functions such as tracing, delayer, stream cache and the like.
 *
 * @version 
 */
public interface RuntimeConfiguration {

    /**
     * Sets whether stream caching is enabled or not (default is disabled).
     *
     * @param cache whether stream caching is enabled or not
     */
    void setStreamCaching(Boolean cache);

    /**
     * Returns whether stream cache is enabled
     *
     * @return <tt>true</tt> if stream cache is enabled
     */
    Boolean isStreamCaching();

    /**
     * Sets whether tracing is enabled or not (default is disabled).
     *
     * @param tracing whether to enable tracing.
     */
    void setTracing(Boolean tracing);

    /**
     * Returns whether tracing enabled
     *
     * @return <tt>true</tt> if tracing is enabled
     */
    Boolean isTracing();

    /**
     * Sets whether message history is enabled or not (default is enabled).
     *
     * @param messageHistory whether message history is enabled
     */
    void setMessageHistory(Boolean messageHistory);

    /**
     * Returns whether message history is enabled
     *
     * @return <tt>true</tt> if message history is enabled
     */
    Boolean isMessageHistory();

    /**
     * Sets whether to log exhausted message body with message history.
     *
     * @param logExhaustedMessageBody whether message body should be logged
     */
    void setLogExhaustedMessageBody(Boolean logExhaustedMessageBody);

    /**
     * Returns whether to log exhausted message body with message history.
     * 
     * @return <tt>true</tt> if logging of message body is enabled
     */
    Boolean isLogExhaustedMessageBody();

    /**
     * Sets whether fault handling is enabled or not (default is disabled).
     *
     * @param handleFault whether to enable fault handling.
     */
    void setHandleFault(Boolean handleFault);

    /**
     * Returns whether fault handling enabled
     *
     * @return <tt>true</tt> if fault handling is enabled
     */
    Boolean isHandleFault();

    /**
     * Sets a delay value in millis that a message is delayed at every step it takes in the route path,
     * slowing the process down to better observe what is occurring
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
     * Sets whether the object should automatically start when Camel starts.
     * <p/>
     * <b>Important:</b> Currently only routes can be disabled, as {@link CamelContext}s are always started.
     * <br/>
     * <b>Note:</b> When setting auto startup <tt>false</tt> on {@link CamelContext} then that takes precedence
     * and <i>no</i> routes is started. You would need to start {@link CamelContext} explicit using
     * the {@link org.apache.camel.CamelContext#start()} method, to start the context, and then
     * you would need to start the routes manually using {@link CamelContext#startRoute(String)}.
     * <p/>
     * Default is <tt>true</tt> to always start up.
     *
     * @param autoStartup whether to start up automatically.
     */
    void setAutoStartup(Boolean autoStartup);

    /**
     * Gets whether the object should automatically start when Camel starts.
     * <p/>
     * <b>Important:</b> Currently only routes can be disabled, as {@link CamelContext}s are always started.
     * <br/>
     * Default is <tt>true</tt> to always start up.
     *
     * @return <tt>true</tt> if object should automatically start
     */
    Boolean isAutoStartup();

    /**
     * Sets the ShutdownRoute option for routes.
     *
     * @param shutdownRoute the option to use.
     */
    void setShutdownRoute(ShutdownRoute shutdownRoute);

    /**
     * Gets the option to use when shutting down the route.
     *
     * @return the option
     */
    ShutdownRoute getShutdownRoute();

    /**
     * Sets the ShutdownRunningTask option to use when shutting down a route.
     *
     * @param shutdownRunningTask the option to use.
     */
    void setShutdownRunningTask(ShutdownRunningTask shutdownRunningTask);

    /**
     * Gets the ShutdownRunningTask option in use when shutting down a route.
     *
     * @return the option
     */
    ShutdownRunningTask getShutdownRunningTask();

    /**
     * Sets whether to allow access to the original message from Camel's error handler,
     * or from {@link org.apache.camel.spi.UnitOfWork#getOriginalInMessage()}.
     * <p/>
     * Turning this off can optimize performance, as defensive copy of the original message is not needed.
     *
     * @param allowUseOriginalMessage the option to use.
     */
    void setAllowUseOriginalMessage(Boolean allowUseOriginalMessage);

    /**
     * Sets whether to allow access to the original message from Camel's error handler,
     * or from {@link org.apache.camel.spi.UnitOfWork#getOriginalInMessage()}.
     * <p/>
     * Turning this off can optimize performance, as defensive copy of the original message is not needed.
     *
     * @return the option
     */
    Boolean isAllowUseOriginalMessage();

}
