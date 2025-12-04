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

package org.apache.camel.api.management.mbean;

import java.util.concurrent.TimeUnit;

import org.apache.camel.api.management.ManagedAttribute;

public interface ManagedShutdownStrategyMBean extends ManagedServiceMBean {

    @ManagedAttribute(description = "Shutdown timeout")
    void setTimeout(long timeout);

    @ManagedAttribute(description = "Shutdown timeout")
    long getTimeout();

    @ManagedAttribute(description = "Shutdown timeout time unit")
    void setTimeUnit(TimeUnit timeUnit);

    @ManagedAttribute(description = "Shutdown timeout time unit")
    TimeUnit getTimeUnit();

    @ManagedAttribute(
            description =
                    "Whether Camel should try to suppress logging during shutdown and timeout was triggered, meaning forced shutdown is happening.")
    void setSuppressLoggingOnTimeout(boolean suppressLoggingOnTimeout);

    @ManagedAttribute(
            description =
                    "Whether Camel should try to suppress logging during shutdown and timeout was triggered, meaning forced shutdown is happening.")
    boolean isSuppressLoggingOnTimeout();

    @ManagedAttribute(description = "Whether to force shutdown of all consumers when a timeout occurred.")
    void setShutdownNowOnTimeout(boolean shutdownNowOnTimeout);

    @ManagedAttribute(description = "Whether to force shutdown of all consumers when a timeout occurred.")
    boolean isShutdownNowOnTimeout();

    @ManagedAttribute(
            description = "Sets whether routes should be shutdown in reverse or the same order as they were started")
    void setShutdownRoutesInReverseOrder(boolean shutdownRoutesInReverseOrder);

    @ManagedAttribute(
            description = "Sets whether routes should be shutdown in reverse or the same order as they were started")
    boolean isShutdownRoutesInReverseOrder();

    @ManagedAttribute(
            description =
                    "Whether to log information about the inflight Exchanges which are still running during a shutdown which didn't complete without the given timeout.")
    void setLogInflightExchangesOnTimeout(boolean logInflightExchangesOnTimeout);

    @ManagedAttribute(
            description =
                    "Whether to log information about the inflight Exchanges which are still running during a shutdown which didn't complete without the given timeout.")
    boolean isLogInflightExchangesOnTimeout();

    @ManagedAttribute(description = "Whether the shutdown strategy is forcing to shutdown")
    boolean isForceShutdown();

    @ManagedAttribute(description = "Whether a timeout has occurred during a shutdown.")
    boolean isTimeoutOccurred();

    @ManagedAttribute(
            description =
                    "logging level used for logging shutdown activity (such as starting and stopping routes). The default logging level is DEBUG.")
    String getLoggingLevel();

    @ManagedAttribute(
            description =
                    "logging level used for logging shutdown activity (such as starting and stopping routes). The default logging level is DEBUG.")
    void setLoggingLevel(String loggingLevel);
}
