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

import org.apache.camel.Exchange;
import org.jspecify.annotations.Nullable;

/**
 * Strategy for handling exceptions that arise in asynchronous or background processing where a normal exception
 * propagation path does not exist, such as in a {@link org.apache.camel.Consumer}'s polling thread.
 * <p/>
 * Unlike the route-level {@link ErrorHandler}, which handles exceptions thrown by processors inside a route,
 * {@code ExceptionHandler} is used by infrastructure code (consumers, schedulers, Quartz jobs, background threads) that
 * cannot propagate exceptions through the normal routing chain. The default implementation logs the exception and
 * continues; custom implementations can re-route, alert, or escalate as needed.
 * <p/>
 * Implementations must <b>not</b> throw exceptions from their {@code handleException} methods, because the handler is
 * typically called from within an existing catch block and a secondary exception would mask the original failure.
 *
 * @see ErrorHandler
 */
public interface ExceptionHandler {

    /**
     * Handles the given exception
     *
     * @param exception the exception
     */
    void handleException(Throwable exception);

    /**
     * Handles the given exception
     *
     * @param message   additional message
     * @param exception the exception
     */
    void handleException(@Nullable String message, Throwable exception);

    /**
     * Handles the given exception
     *
     * @param message   additional message
     * @param exchange  exchange which cause the exception
     * @param exception the exception
     */
    void handleException(@Nullable String message, @Nullable Exchange exchange, Throwable exception);
}
