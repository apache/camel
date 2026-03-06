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

import java.time.Instant;

/**
 * A snapshot of an error that occurred during message routing.
 * <p/>
 * This is an immutable value object that does not hold references to the original exchange or exception.
 */
public interface ErrorRegistryEntry {

    /**
     * The exchange id
     */
    String exchangeId();

    /**
     * The route id where the error occurred
     */
    String routeId();

    /**
     * The endpoint URI where the error occurred (if available)
     */
    String endpointUri();

    /**
     * The timestamp when the error occurred
     */
    Instant timestamp();

    /**
     * Whether the error was handled by an error handler or onException
     */
    boolean handled();

    /**
     * The fully qualified class name of the exception
     */
    String exceptionType();

    /**
     * The exception message
     */
    String exceptionMessage();

    /**
     * The stack trace lines, or {@code null} if stack trace capture is disabled.
     * <p/>
     * Each element represents one line of the stack trace.
     */
    String[] stackTrace();
}
