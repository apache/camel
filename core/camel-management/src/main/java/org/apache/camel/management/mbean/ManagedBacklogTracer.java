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
package org.apache.camel.management.mbean;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedBacklogTracerMBean;
import org.apache.camel.impl.debugger.BacklogTracer;
import org.apache.camel.spi.BacklogTracerEventMessage;
import org.apache.camel.spi.ManagementStrategy;

@ManagedResource(description = "Managed BacklogTracer")
public class ManagedBacklogTracer implements ManagedBacklogTracerMBean {
    private final CamelContext camelContext;
    private final BacklogTracer backlogTracer;

    public ManagedBacklogTracer(CamelContext camelContext, BacklogTracer backlogTracer) {
        this.camelContext = camelContext;
        this.backlogTracer = backlogTracer;
    }

    public void init(ManagementStrategy strategy) {
        // do nothing
    }

    public CamelContext getContext() {
        return camelContext;
    }

    public BacklogTracer getBacklogTracer() {
        return backlogTracer;
    }

    public boolean getEnabled() {
        return backlogTracer.isEnabled();
    }

    @Override
    public String getCamelId() {
        return camelContext.getName();
    }

    @Override
    public String getCamelManagementName() {
        return camelContext.getManagementName();
    }

    @Override
    public boolean isStandby() {
        return backlogTracer.isStandby();
    }

    @Override
    public void setEnabled(boolean enabled) {
        backlogTracer.setEnabled(enabled);
    }

    @Override
    public boolean isEnabled() {
        return backlogTracer.isEnabled();
    }

    @Override
    public int getBacklogSize() {
        return backlogTracer.getBacklogSize();
    }

    @Override
    public void setBacklogSize(int backlogSize) {
        backlogTracer.setBacklogSize(backlogSize);
    }

    @Override
    public boolean isRemoveOnDump() {
        return backlogTracer.isRemoveOnDump();
    }

    @Override
    public void setRemoveOnDump(boolean removeOnDump) {
        backlogTracer.setRemoveOnDump(removeOnDump);
    }

    @Override
    public void setTracePattern(String pattern) {
        backlogTracer.setTracePattern(pattern);
    }

    @Override
    public String getTracePattern() {
        return backlogTracer.getTracePattern();
    }

    @Override
    public void setTraceFilter(String predicate) {
        backlogTracer.setTraceFilter(predicate);
    }

    @Override
    public String getTraceFilter() {
        return backlogTracer.getTraceFilter();
    }

    @Override
    public long getTraceCounter() {
        return backlogTracer.getTraceCounter();
    }

    @Override
    public void resetTraceCounter() {
        backlogTracer.resetTraceCounter();
    }

    @Override
    public long getQueueSize() {
        return backlogTracer.getQueueSize();
    }

    @Override
    public int getBodyMaxChars() {
        return backlogTracer.getBodyMaxChars();
    }

    @Override
    public void setBodyMaxChars(int bodyMaxChars) {
        backlogTracer.setBodyMaxChars(bodyMaxChars);
    }

    @Override
    public boolean isBodyIncludeStreams() {
        return backlogTracer.isBodyIncludeStreams();
    }

    @Override
    public void setBodyIncludeStreams(boolean bodyIncludeStreams) {
        backlogTracer.setBodyIncludeStreams(bodyIncludeStreams);
    }

    @Override
    public boolean isBodyIncludeFiles() {
        return backlogTracer.isBodyIncludeFiles();
    }

    @Override
    public void setBodyIncludeFiles(boolean bodyIncludeFiles) {
        backlogTracer.setBodyIncludeFiles(bodyIncludeFiles);
    }

    @Override
    public boolean isIncludeExchangeProperties() {
        return backlogTracer.isIncludeExchangeProperties();
    }

    @Override
    public void setIncludeExchangeProperties(boolean includeExchangeProperties) {
        backlogTracer.setIncludeExchangeProperties(includeExchangeProperties);
    }

    @Override
    public boolean isIncludeExchangeVariables() {
        return backlogTracer.isIncludeExchangeVariables();
    }

    @Override
    public void setIncludeExchangeVariables(boolean includeExchangeVariables) {
        backlogTracer.setIncludeExchangeVariables(includeExchangeVariables);
    }

    @Override
    public boolean isTraceRests() {
        return backlogTracer.isTraceRests();
    }

    @Override
    public boolean isTraceTemplates() {
        return backlogTracer.isTraceTemplates();
    }

    @Override
    public List<BacklogTracerEventMessage> dumpTracedMessages(String nodeOrRouteId) {
        return backlogTracer.dumpTracedMessages(nodeOrRouteId);
    }

    @Override
    public List<BacklogTracerEventMessage> dumpAllTracedMessages() {
        return backlogTracer.dumpAllTracedMessages();
    }

    @Override
    public String dumpTracedMessagesAsXml(String nodeOrRouteId) {
        return backlogTracer.dumpTracedMessagesAsXml(nodeOrRouteId);
    }

    @Override
    public String dumpAllTracedMessagesAsXml() {
        return backlogTracer.dumpAllTracedMessagesAsXml();
    }

    @Override
    public void clear() {
        backlogTracer.clear();
    }
}
