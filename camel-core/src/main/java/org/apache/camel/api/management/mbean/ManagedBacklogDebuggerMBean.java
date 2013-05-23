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
package org.apache.camel.api.management.mbean;

import java.util.Set;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;

public interface ManagedBacklogDebuggerMBean {

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
    void addBreakpoint(String nodeId, boolean internal);

    @ManagedOperation(description = "Remote the breakpoint from the given node id")
    void removeBreakpoint(String nodeId);

    @ManagedOperation(description = "Resume running from the suspended breakpoint at the given node id")
    void resumeBreakpoint(String nodeId);

    @ManagedOperation(description = "Resume running any suspended breakpoints, and exits step mode")
    void resumeAll();

    @ManagedOperation(description = "Starts single step debugging from the suspended breakpoint at the given node id")
    void stepBreakpoint(String nodeId);

    @ManagedAttribute(description = "Whether currently in step mode")
    boolean isSingleStepMode();

    @ManagedOperation(description = "Steps to next node in step mode")
    void step();

    @ManagedOperation(description = "Return the node ids which has breakpoints")
    Set<String> getBreakpoints(boolean includeInternal);

    @ManagedOperation(description = "Return the node ids which is currently suspended")
    Set<String> getSuspendedBreakpointNodeIds();

    @ManagedOperation(description = "Suspend a breakpoint")
    public void suspendBreakpoint(String nodeId);

    @ManagedOperation(description = "Activate a breakpoint")
    public void activateBreakpoint(String nodeId);

    @ManagedOperation(description = "Dumps the messages in xml format from the suspended breakpoint at the given node")
    String dumpTracedMessagesAsXml(String nodeId);

    @ManagedAttribute(description = "Number of total debugged messages")
    long getDebugCounter();

    @ManagedOperation(description = "Resets the debug counter")
    void resetDebugCounter();

}
