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

import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.MessageHistory;
import org.apache.camel.NamedNode;
import org.apache.camel.StaticService;

/**
 * A factory to create {@link MessageHistory} instances.
 */
public interface MessageHistoryFactory extends StaticService, CamelContextAware {

    /**
     * Creates a new {@link MessageHistory}
     *
     * @param routeId   the route id
     * @param node      the node in the route
     * @param timestamp the time the message processed at this node.
     * @param exchange  the current exchange
     * @return a new {@link MessageHistory}
     */
    MessageHistory newMessageHistory(String routeId, NamedNode node, long timestamp, Exchange exchange);

    /**
     * Whether to make a copy of the message in the {@link MessageHistory}.
     * By default this is turned off. Beware that you should not mutate or change the content
     * on the copied message, as its purpose is as a read-only view of the message.
     */
    boolean isCopyMessage();

    /**
     * Sets whether to make a copy of the message in the {@link MessageHistory}.
     * By default this is turned off. Beware that you should not mutate or change the content
     * on the copied message, as its purpose is as a read-only view of the message.
     */
    void setCopyMessage(boolean copyMessage);

    /**
     * An optional pattern to filter which nodes to trace in this message history. By default all nodes are included.
     * To only include nodes that are Step EIPs then use the EIP shortname, eg step.
     * You can also include multiple nodes separated by comma, eg step,wiretap,to
     */
    String getNodePattern();

    /**
     * An optional pattern to filter which nodes to trace in this message history. By default all nodes are included.
     * To only include nodes that are Step EIPs then use the EIP shortname, eg step.
     * You can also include multiple nodes separated by comma, eg step,wiretap,to
     */
    void setNodePattern(String nodePattern);

}
