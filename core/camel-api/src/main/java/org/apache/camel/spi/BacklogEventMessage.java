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

import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Base interface for backlog event messages that capture exchange snapshots at a point in time, without retaining
 * references to the live {@link org.apache.camel.Exchange}.
 * <p/>
 * This is the shared contract between {@link BacklogTracerEventMessage} and {@link BacklogErrorEventMessage}.
 *
 * @since 4.21
 */
public interface BacklogEventMessage {

    /**
     * Unique monotonically increasing identifier for this event message.
     */
    long getUid();

    /**
     * Timestamp when the event was captured (milliseconds since epoch).
     */
    long getTimestamp();

    /**
     * Source file location of the node where the event occurred (e.g. "MyRoute.java:42"), or {@code null} if source
     * location tracking is not enabled.
     */
    @Nullable
    String getLocation();

    /**
     * The id of the route where the event occurred. For errors this is the route where the failure happened, which may
     * differ from {@link #getFromRouteId()} if the exchange was routed across multiple routes.
     */
    String getRouteId();

    /**
     * The id of the route where the exchange originally started (the consumer route), or {@code null} if not available.
     */
    @Nullable
    String getFromRouteId();

    /**
     * The unique exchange identifier.
     */
    String getExchangeId();

    /**
     * The URI of the endpoint the exchange was being sent to (producer endpoint) when the event was captured. This can
     * be {@code null} when the failure occurs before any send attempt (e.g. a direct {@code throwException} in the
     * route). This does <b>not</b> refer to the route's consumer (from) endpoint.
     */
    @Nullable
    String getEndpointUri();

    /**
     * The id of the EIP processor node where the event occurred, or {@code null} if not available.
     */
    @Nullable
    String getToNode();

    /**
     * The name of the thread that was processing the exchange when this event was captured.
     */
    String getProcessingThreadName();

    /**
     * A detached snapshot of the exchange message content as JSon, including the message body, headers, and optionally
     * exchange properties and variables.
     */
    String getMessageAsJSon();

    /**
     * Whether this event has an associated exception.
     */
    boolean hasException();

    /**
     * The exception details as JSon (type, message, and stack trace), or {@code null} if no exception is present.
     */
    @Nullable
    String getExceptionAsJSon();

    /**
     * Dumps the full event message as a pretty-printed JSon string.
     *
     * @param  indent number of spaces to indent
     * @return        JSon representation of this event
     */
    String toJSon(int indent);

    /**
     * The full event message as a {@link Map} suitable for JSon serialization, containing all fields of this event.
     */
    Map<String, Object> asJSon();
}
