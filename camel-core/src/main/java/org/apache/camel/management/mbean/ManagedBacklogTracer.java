/**
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
import org.apache.camel.api.management.mbean.BacklogTracerEventMessage;
import org.apache.camel.api.management.mbean.ManagedBacklogTracerMBean;
import org.apache.camel.processor.interceptor.BacklogTracer;
import org.apache.camel.spi.ManagementStrategy;

/**
 * @version 
 */
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

    public String getCamelId() {
        return camelContext.getName();
    }

    public String getCamelManagementName() {
        return camelContext.getManagementName();
    }

    public void setEnabled(boolean enabled) {
        backlogTracer.setEnabled(enabled);
    }

    public boolean isEnabled() {
        return backlogTracer.isEnabled();
    }

    public int getBacklogSize() {
        return backlogTracer.getBacklogSize();
    }

    public void setBacklogSize(int backlogSize) {
        backlogTracer.setBacklogSize(backlogSize);
    }

    public boolean isRemoveOnDump() {
        return backlogTracer.isRemoveOnDump();
    }

    public void setRemoveOnDump(boolean removeOnDump) {
        backlogTracer.setRemoveOnDump(removeOnDump);
    }

    public void setTracePattern(String pattern) {
        backlogTracer.setTracePattern(pattern);
    }

    public String getTracePattern() {
        return backlogTracer.getTracePattern();
    }

    public void setTraceFilter(String predicate) {
        backlogTracer.setTraceFilter(predicate);
    }

    public String getTraceFilter() {
        return backlogTracer.getTraceFilter();
    }

    public long getTraceCounter() {
        return backlogTracer.getTraceCounter();
    }

    public void resetTraceCounter() {
        backlogTracer.resetTraceCounter();
    }

    public int getBodyMaxChars() {
        return backlogTracer.getBodyMaxChars();
    }

    public void setBodyMaxChars(int bodyMaxChars) {
        backlogTracer.setBodyMaxChars(bodyMaxChars);
    }

    public boolean isBodyIncludeStreams() {
        return backlogTracer.isBodyIncludeStreams();
    }

    public void setBodyIncludeStreams(boolean bodyIncludeStreams) {
        backlogTracer.setBodyIncludeStreams(bodyIncludeStreams);
    }

    public boolean isBodyIncludeFiles() {
        return backlogTracer.isBodyIncludeFiles();
    }

    public void setBodyIncludeFiles(boolean bodyIncludeFiles) {
        backlogTracer.setBodyIncludeFiles(bodyIncludeFiles);
    }

    public List<BacklogTracerEventMessage> dumpTracedMessages(String nodeOrRouteId) {
        return backlogTracer.dumpTracedMessages(nodeOrRouteId);
    }

    public List<BacklogTracerEventMessage> dumpAllTracedMessages() {
        return backlogTracer.dumpAllTracedMessages();
    }

    public String dumpTracedMessagesAsXml(String nodeOrRouteId) {
        return backlogTracer.dumpTracedMessagesAsXml(nodeOrRouteId);
    }

    public String dumpAllTracedMessagesAsXml() {
        return backlogTracer.dumpAllTracedMessagesAsXml();
    }

    public void clear() {
        backlogTracer.clear();
    }
}
