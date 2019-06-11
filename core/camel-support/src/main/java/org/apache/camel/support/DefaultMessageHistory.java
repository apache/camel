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
package org.apache.camel.support;

import org.apache.camel.Message;
import org.apache.camel.MessageHistory;
import org.apache.camel.NamedNode;

/**
 * Default {@link org.apache.camel.MessageHistory}.
 */
public class DefaultMessageHistory implements MessageHistory {

    private final String routeId;
    private final NamedNode node;
    private final String nodeId;
    private final long timestamp;
    private final Message message;
    private long elapsed;

    public DefaultMessageHistory(String routeId, NamedNode node, long timestamp) {
        this(routeId, node, timestamp, null);
    }

    public DefaultMessageHistory(String routeId, NamedNode node, long timestamp, Message message) {
        this.routeId = routeId;
        this.node = node;
        this.nodeId = node.getId();
        this.timestamp = timestamp;
        this.message = message;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public NamedNode getNode() {
        return node;
    }

    @Override
    public long getTime() {
        return timestamp;
    }

    @Override
    public long getElapsed() {
        return elapsed;
    }

    @Override
    public void nodeProcessingDone() {
        if (timestamp > 0) {
            elapsed = System.currentTimeMillis() - timestamp;
        }
    }

    @Override
    public Message getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "DefaultMessageHistory["
                + "routeId=" + routeId
                + ", node=" + nodeId
                + ']';
    }
}
