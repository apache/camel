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

import org.jspecify.annotations.Nullable;

/**
 * Represents an error event captured by the {@link ErrorRegistry}.
 * <p/>
 * This extends {@link BacklogEventMessage} with error-specific information such as the exception, whether the error was
 * handled by an error handler, the route group, and the message history trace.
 *
 * @since 4.21
 */
public interface BacklogErrorEventMessage extends BacklogEventMessage {

    /**
     * The route group of the route where the error occurred, or {@code null} if the route has no group assigned.
     */
    @Nullable
    String getRouteGroup();

    /**
     * The actual exception that caused the error. This is the live {@link Throwable} instance, not a serialized copy.
     */
    Throwable getException();

    /**
     * Whether the error was handled by an error handler (e.g. dead letter channel, onException) so the exchange
     * completed without propagating the exception to the caller. {@code false} means the exception was not handled and
     * the exchange failed.
     */
    boolean isHandled();

    /**
     * The fully qualified class name of the exception (e.g. "java.lang.IllegalArgumentException").
     */
    String getExceptionType();

    /**
     * The exception's detail message from {@link Throwable#getMessage()}, or {@code null} if the exception has no
     * message.
     */
    @Nullable
    String getExceptionMessage();

    /**
     * The message history trace captured at the time of the error, or {@code null} if message history is not enabled on
     * the CamelContext.
     * <p/>
     * Each element represents one step in the routing history in the format "routeId[nodeId] (elapsed ms)".
     */
    String @Nullable [] getMessageHistory();
}
