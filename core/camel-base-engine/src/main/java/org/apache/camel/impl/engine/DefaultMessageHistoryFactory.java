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
package org.apache.camel.impl.engine;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.MessageHistory;
import org.apache.camel.NamedNode;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.MessageHistoryFactory;
import org.apache.camel.support.DefaultMessageHistory;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.service.ServiceSupport;

@ManagedResource(description = "Managed MessageHistoryFactory")
public class DefaultMessageHistoryFactory extends ServiceSupport implements MessageHistoryFactory {

    private CamelContext camelContext;
    private boolean copyMessage;
    private String nodePattern;
    private volatile String[] nodePatternParts;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public MessageHistory newMessageHistory(String routeId, NamedNode node, Exchange exchange) {
        if (nodePatternParts != null) {
            String name = node.getShortName();
            for (String part : nodePatternParts) {
                boolean match = PatternHelper.matchPattern(name, part);
                if (!match) {
                    return null;
                }
            }
        }

        Message msg = null;
        if (copyMessage) {
            msg = exchange.getMessage().copy();
        }

        return new DefaultMessageHistory(routeId, node, msg);
    }

    @ManagedAttribute(description = "Whether message history is enabled")
    public boolean isEnabled() {
        return camelContext != null ? camelContext.isMessageHistory() : false;
    }

    @Override
    @ManagedAttribute(description = "Whether a copy of the message is included in the message history")
    public boolean isCopyMessage() {
        return copyMessage;
    }

    @Override
    @ManagedAttribute(description = "Whether a copy of the message is included in the message history")
    public void setCopyMessage(boolean copyMessage) {
        this.copyMessage = copyMessage;
    }

    @Override
    @ManagedAttribute(description = "Pattern to filter EIPs")
    public String getNodePattern() {
        return nodePattern;
    }

    @Override
    @ManagedAttribute(description = "Pattern to filter EIPs")
    public void setNodePattern(String nodePattern) {
        this.nodePattern = nodePattern;
        if (nodePattern != null) {
            this.nodePatternParts = nodePattern.split(",");
        }
    }

}
