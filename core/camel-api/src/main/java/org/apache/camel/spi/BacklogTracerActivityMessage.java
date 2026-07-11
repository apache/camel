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

import java.util.List;

/**
 * Lightweight summary of a completed exchange captured by the {@link BacklogTracer} activity queue.
 * <p>
 * Unlike {@link BacklogTracerEventMessage} which captures full message snapshots (body, headers, properties) at each
 * route node, activity messages contain only exchange-level metadata and a list of remote endpoints that were called
 * during the exchange lifecycle. This makes them suitable for live monitoring dashboards without the overhead of full
 * tracing.
 *
 * @since 4.22
 */
public interface BacklogTracerActivityMessage {

    /**
     * Unique trace counter for this activity entry.
     */
    long getUid();

    /**
     * Timestamp when the exchange was created.
     */
    long getTimestamp();

    /**
     * The exchange id.
     */
    String getExchangeId();

    /**
     * The route id where the exchange originated.
     */
    String getRouteId();

    /**
     * The endpoint URI of the route consumer (the "from" endpoint).
     */
    String getFromEndpointUri();

    /**
     * Total elapsed time in milliseconds for the exchange.
     */
    long getElapsed();

    /**
     * Whether the exchange failed.
     */
    boolean isFailed();

    /**
     * The exception message if the exchange failed, or null if successful.
     */
    String getExceptionMessage();

    /**
     * List of remote endpoints that were called during the exchange lifecycle.
     * <p>
     * Only remote endpoints are captured (endpoints where {@link org.apache.camel.Endpoint#isRemote()} returns true).
     * Internal endpoints like direct, seda, timer, and log are excluded.
     */
    List<EndpointSend> getEndpointSends();

    /**
     * A single remote endpoint call captured during the exchange lifecycle.
     *
     * @since 4.22
     */
    interface EndpointSend {

        /**
         * The endpoint URI that was called.
         */
        String getEndpointUri();

        /**
         * Whether this is a remote endpoint.
         */
        boolean isRemoteEndpoint();

        /**
         * Time taken in milliseconds for this endpoint call.
         */
        long getElapsed();
    }
}
