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
package org.apache.camel.impl.debugger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.camel.spi.BacklogTracerActivityMessage;

/**
 * Default implementation of {@link BacklogTracerActivityMessage}.
 */
public final class DefaultBacklogTracerActivityMessage implements BacklogTracerActivityMessage {

    private final long uid;
    private final long timestamp;
    private final String exchangeId;
    private String routeId;
    private String fromEndpointUri;
    private final List<DefaultEndpointSend> endpointSends = new ArrayList<>();
    private long elapsed;
    private boolean failed;
    private String exceptionMessage;

    public DefaultBacklogTracerActivityMessage(long uid, long timestamp, String exchangeId) {
        this.uid = uid;
        this.timestamp = timestamp;
        this.exchangeId = exchangeId;
    }

    @Override
    public long getUid() {
        return uid;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String getExchangeId() {
        return exchangeId;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public String getFromEndpointUri() {
        return fromEndpointUri;
    }

    @Override
    public long getElapsed() {
        return elapsed;
    }

    @Override
    public boolean isFailed() {
        return failed;
    }

    @Override
    public String getExceptionMessage() {
        return exceptionMessage;
    }

    @Override
    public List<EndpointSend> getEndpointSends() {
        synchronized (endpointSends) {
            return Collections.unmodifiableList(new ArrayList<>(endpointSends));
        }
    }

    public void addEndpointSend(String endpointUri, boolean remoteEndpoint, long elapsed) {
        synchronized (endpointSends) {
            endpointSends.add(new DefaultEndpointSend(endpointUri, remoteEndpoint, elapsed));
        }
    }

    public void complete(
            String routeId, String fromEndpointUri,
            long elapsed, boolean failed, String exceptionMessage) {
        this.routeId = routeId;
        this.fromEndpointUri = fromEndpointUri;
        this.elapsed = elapsed;
        this.failed = failed;
        this.exceptionMessage = exceptionMessage;
    }

    /**
     * Default implementation of {@link EndpointSend}.
     */
    public static final class DefaultEndpointSend implements EndpointSend {

        private final String endpointUri;
        private final boolean remoteEndpoint;
        private final long elapsed;

        public DefaultEndpointSend(String endpointUri, boolean remoteEndpoint, long elapsed) {
            this.endpointUri = endpointUri;
            this.remoteEndpoint = remoteEndpoint;
            this.elapsed = elapsed;
        }

        @Override
        public String getEndpointUri() {
            return endpointUri;
        }

        @Override
        public boolean isRemoteEndpoint() {
            return remoteEndpoint;
        }

        @Override
        public long getElapsed() {
            return elapsed;
        }
    }
}
