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
package org.apache.camel.api.management.mbean;

import java.util.Set;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;

public interface ManagedBacklogDebuggerMBean {

    @ManagedAttribute(description = "Camel ID")
    String getCamelId();

    @ManagedAttribute(description = "Camel ManagementName")
    String getCamelManagementName();

    @ManagedAttribute(description = "Logging Level")
    String getLoggingLevel();

    @ManagedAttribute(description = "Logging Level")
    void setLoggingLevel(String level);

    @ManagedAttribute(description = "Is debugger enabled")
    boolean isEnabled();

    @ManagedOperation(description = "Enable the debugger")
    void enableDebugger();

    @ManagedOperation(description = "Disable the debugger")
    void disableDebugger();

    @ManagedOperation(description = "Add a breakpoint at the given node id")
    void addBreakpoint(String nodeId);

    @ManagedOperation(description = "Add a conditional breakpoint at the given node id")
    void addConditionalBreakpoint(String nodeId, String language, String predicate);

    @ManagedOperation(description = "Remote the breakpoint from the given node id (will resume suspend breakpoint first)")
    void removeBreakpoint(String nodeId);

    @ManagedOperation(description = "Remote all breakpoints (will resume all suspend breakpoints first and exists single step mode)")
    void removeAllBreakpoints();

    @ManagedOperation(description = "Resume running from the suspended breakpoint at the given node id")
    void resumeBreakpoint(String nodeId);

    @ManagedOperation(description = "Updates the message body (uses same type as old body) on the suspended breakpoint at the given node id")
    void setMessageBodyOnBreakpoint(String nodeId, Object body);

    @ManagedOperation(description = "Updates the message body (with a new type) on the suspended breakpoint at the given node id")
    void setMessageBodyOnBreakpoint(String nodeId, Object body, String type);

    @ManagedOperation(description = "Removes the message body on the suspended breakpoint at the given node id")
    void removeMessageBodyOnBreakpoint(String nodeId);

    @ManagedOperation(description = "Updates/adds the message header (uses same type as old header value) on the suspended breakpoint at the given node id")
    void setMessageHeaderOnBreakpoint(String nodeId, String headerName, Object value);

    @ManagedOperation(description = "Removes the message header on the suspended breakpoint at the given node id")
    void removeMessageHeaderOnBreakpoint(String nodeId, String headerName);

    @ManagedOperation(description = "Updates/adds the message header (with a new type) on the suspended breakpoint at the given node id")
    void setMessageHeaderOnBreakpoint(String nodeId, String headerName, Object value, String type);

    @ManagedOperation(description = "Resume running any suspended breakpoints, and exits step mode")
    void resumeAll();

    @ManagedOperation(description = "Starts single step debugging from the suspended breakpoint at the given node id")
    void stepBreakpoint(String nodeId);

    @ManagedAttribute(description = "Whether currently in step mode")
    boolean isSingleStepMode();

    @ManagedOperation(description = "Steps to next node in step mode")
    void step();

    @ManagedOperation(description = "Return the node ids which has breakpoints")
    Set<String> getBreakpoints();

    @ManagedOperation(description = "Return the node ids which is currently suspended")
    Set<String> getSuspendedBreakpointNodeIds();

    @ManagedOperation(description = "Disables a breakpoint")
    void disableBreakpoint(String nodeId);

    @ManagedOperation(description = "Enables a breakpoint which has been disabled")
    void enableBreakpoint(String nodeId);

    @ManagedAttribute(description = "Number of maximum chars in the message body in the trace message. Use zero or negative value to have unlimited size.")
    int getBodyMaxChars();

    @ManagedAttribute(description = "Number of maximum chars in the message body in the trace message. Use zero or negative value to have unlimited size.")
    void setBodyMaxChars(int bodyMaxChars);
    
    @ManagedAttribute(description = "Fallback Timeout in seconds when block the message processing in Camel.")
    long getFallbackTimeout();
    
    @ManagedAttribute(description = "Fallback Timeout in seconds when block the message processing in Camel.")
    void setFallbackTimeout(long fallbackTimeout);
    
    @ManagedAttribute(description = "Whether to include stream based message body in the trace message.")
    boolean isBodyIncludeStreams();

    @ManagedAttribute(description = "Whether to include stream based message body in the trace message.")
    void setBodyIncludeStreams(boolean bodyIncludeStreams);

    @ManagedAttribute(description = "Whether to include file based message body in the trace message.")
    boolean isBodyIncludeFiles();

    @ManagedAttribute(description = "Whether to include file based message body in the trace message.")
    void setBodyIncludeFiles(boolean bodyIncludeFiles);

    @ManagedOperation(description = "Dumps the messages in xml format from the suspended breakpoint at the given node")
    String dumpTracedMessagesAsXml(String nodeId);

    @ManagedAttribute(description = "Number of total debugged messages")
    long getDebugCounter();

    @ManagedOperation(description = "Resets the debug counter")
    void resetDebugCounter();

    @ManagedOperation(description = "Used for validating if a given predicate is valid or not")
    String validateConditionalBreakpoint(String language, String predicate);

}
