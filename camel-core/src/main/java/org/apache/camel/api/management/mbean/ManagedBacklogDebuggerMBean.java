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

    @ManagedAttribute(description = "Is debugger enabled")
    boolean isEnabled();

    @ManagedOperation(description = "Enable the debugger")
    void enableDebugger();

    @ManagedOperation(description = "Disable the debugger")
    void disableDebugger();

    @ManagedOperation(description = "Add a breakpoint at the given node id")
    void addBreakpoint(String nodeId);

    @ManagedOperation(description = "Remote the breakpoint from the given node id")
    void removeBreakpoint(String nodeId);

    @ManagedOperation(description = "Continue debugging the suspended breakpoints at the given node id")
    void continueBreakpoint(String nodeId);

    @ManagedOperation(description = "Return the node ids which is currently suspended")
    Set<String> getSuspendedBreakpointNodeIds();

    @ManagedOperation(description = "Dumps the messages in xml format from the suspended breakpoint at the given node")
    String dumpTracedMessagesAsXml(String nodeId);

    @ManagedAttribute(description = "Number of total debugged messages")
    public long getDebugCounter();

    @ManagedOperation(description = "Resets the debug counter")
    public void resetDebugCounter();

}
