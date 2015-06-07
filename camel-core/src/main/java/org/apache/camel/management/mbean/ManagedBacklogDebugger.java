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

import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedBacklogDebuggerMBean;
import org.apache.camel.processor.interceptor.BacklogDebugger;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.ObjectHelper;

@ManagedResource(description = "Managed BacklogDebugger")
public class ManagedBacklogDebugger implements ManagedBacklogDebuggerMBean {

    private final CamelContext camelContext;
    private final BacklogDebugger backlogDebugger;

    public ManagedBacklogDebugger(CamelContext camelContext, BacklogDebugger backlogDebugger) {
        this.camelContext = camelContext;
        this.backlogDebugger = backlogDebugger;
    }

    public void init(ManagementStrategy strategy) {
        // do nothing
    }

    public CamelContext getContext() {
        return camelContext;
    }

    public BacklogDebugger getBacklogDebugger() {
        return backlogDebugger;
    }

    public String getCamelId() {
        return camelContext.getName();
    }

    public String getCamelManagementName() {
        return camelContext.getManagementName();
    }

    public String getLoggingLevel() {
        return backlogDebugger.getLoggingLevel();
    }

    public void setLoggingLevel(String level) {
        backlogDebugger.setLoggingLevel(level);
    }

    public boolean isEnabled() {
        return backlogDebugger.isEnabled();
    }

    public void enableDebugger() {
        backlogDebugger.enableDebugger();
    }

    public void disableDebugger() {
        backlogDebugger.disableDebugger();
    }

    public void addBreakpoint(String nodeId) {
        backlogDebugger.addBreakpoint(nodeId);
    }

    public void addConditionalBreakpoint(String nodeId, String language, String predicate) {
        backlogDebugger.addConditionalBreakpoint(nodeId, language, predicate);
    }

    public void removeBreakpoint(String nodeId) {
        backlogDebugger.removeBreakpoint(nodeId);
    }

    public void removeAllBreakpoints() {
        backlogDebugger.removeAllBreakpoints();
    }

    public Set<String> getBreakpoints() {
        return backlogDebugger.getBreakpoints();
    }

    public void resumeBreakpoint(String nodeId) {
        backlogDebugger.resumeBreakpoint(nodeId);
    }

    public void setMessageBodyOnBreakpoint(String nodeId, Object body) {
        backlogDebugger.setMessageBodyOnBreakpoint(nodeId, body);
    }

    public void setMessageBodyOnBreakpoint(String nodeId, Object body, String type) {
        try {
            Class<?> classType = camelContext.getClassResolver().resolveMandatoryClass(type);
            backlogDebugger.setMessageBodyOnBreakpoint(nodeId, body, classType);
        } catch (ClassNotFoundException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    public void removeMessageBodyOnBreakpoint(String nodeId) {
        backlogDebugger.removeMessageBodyOnBreakpoint(nodeId);
    }

    public void setMessageHeaderOnBreakpoint(String nodeId, String headerName, Object value) {
        try {
            backlogDebugger.setMessageHeaderOnBreakpoint(nodeId, headerName, value);
        } catch (NoTypeConversionAvailableException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    public void setMessageHeaderOnBreakpoint(String nodeId, String headerName, Object value, String type) {
        try {
            Class<?> classType = camelContext.getClassResolver().resolveMandatoryClass(type);
            backlogDebugger.setMessageHeaderOnBreakpoint(nodeId, headerName, value, classType);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    public void removeMessageHeaderOnBreakpoint(String nodeId, String headerName) {
        backlogDebugger.removeMessageHeaderOnBreakpoint(nodeId, headerName);
    }

    public void resumeAll() {
        backlogDebugger.resumeAll();
    }

    public void stepBreakpoint(String nodeId) {
        backlogDebugger.stepBreakpoint(nodeId);
    }

    public boolean isSingleStepMode() {
        return backlogDebugger.isSingleStepMode();
    }

    public void step() {
        backlogDebugger.step();
    }

    public Set<String> getSuspendedBreakpointNodeIds() {
        return backlogDebugger.getSuspendedBreakpointNodeIds();
    }

    public void disableBreakpoint(String nodeId) {
        backlogDebugger.disableBreakpoint(nodeId);
    }

    public void enableBreakpoint(String nodeId) {
        backlogDebugger.enableBreakpoint(nodeId);
    }

    public int getBodyMaxChars() {
        return backlogDebugger.getBodyMaxChars();
    }

    public void setBodyMaxChars(int bodyMaxChars) {
        backlogDebugger.setBodyMaxChars(bodyMaxChars);
    }

    public boolean isBodyIncludeStreams() {
        return backlogDebugger.isBodyIncludeStreams();
    }

    public void setBodyIncludeStreams(boolean bodyIncludeStreams) {
        backlogDebugger.setBodyIncludeStreams(bodyIncludeStreams);
    }

    public boolean isBodyIncludeFiles() {
        return backlogDebugger.isBodyIncludeFiles();
    }

    public void setBodyIncludeFiles(boolean bodyIncludeFiles) {
        backlogDebugger.setBodyIncludeFiles(bodyIncludeFiles);
    }

    public String dumpTracedMessagesAsXml(String nodeId) {
        return backlogDebugger.dumpTracedMessagesAsXml(nodeId);
    }

    public long getDebugCounter() {
        return backlogDebugger.getDebugCounter();
    }

    public void resetDebugCounter() {
        backlogDebugger.resetDebugCounter();
    }

    public String validateConditionalBreakpoint(String language, String predicate) {
        Language lan = null;
        try {
            lan = camelContext.resolveLanguage(language);
            lan.createPredicate(predicate);
            return null;
        } catch (Exception e) {
            if (lan == null) {
                return e.getMessage();
            } else {
                return "Invalid syntax " + predicate + " due: " + e.getMessage();
            }
        }
    }
    
    public long getFallbackTimeout() {
        return backlogDebugger.getFallbackTimeout();
    }
   
    public void setFallbackTimeout(long fallbackTimeout) {
        backlogDebugger.setFallbackTimeout(fallbackTimeout);
    }
}
