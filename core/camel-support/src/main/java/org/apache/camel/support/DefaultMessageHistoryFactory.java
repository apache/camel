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

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.MessageHistory;
import org.apache.camel.NamedNode;
import org.apache.camel.spi.MessageHistoryFactory;

public class DefaultMessageHistoryFactory implements MessageHistoryFactory {

    private boolean copyMessage;
    private String nodePattern;

    @Override
    public MessageHistory newMessageHistory(String routeId, NamedNode node, long timestamp, Exchange exchange) {
        if (nodePattern != null) {
            String name = node.getShortName();
            String[] parts = nodePattern.split(",");
            for (String part : parts) {
                boolean match = PatternHelper.matchPattern(name, part);
                if (!match) {
                    return null;
                }
            }
        }

        Message target = null;
        if (copyMessage) {
            target = exchange.getMessage().copy();
        }

        return new DefaultMessageHistory(routeId, node, timestamp, target);
    }

    @Override
    public boolean isCopyMessage() {
        return copyMessage;
    }

    @Override
    public void setCopyMessage(boolean copyMessage) {
        this.copyMessage = copyMessage;
    }

    @Override
    public String getNodePattern() {
        return nodePattern;
    }

    @Override
    public void setNodePattern(String nodePattern) {
        this.nodePattern = nodePattern;
    }
}
