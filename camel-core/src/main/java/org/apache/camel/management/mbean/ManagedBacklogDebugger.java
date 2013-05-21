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
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedBacklogDebuggerMBean;
import org.apache.camel.processor.interceptor.BacklogDebugger;
import org.apache.camel.spi.ManagementStrategy;

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

    public void removeBreakpoint(String nodeId) {
        backlogDebugger.removeBreakpoint(nodeId);
    }

    public void continueBreakpoint(String nodeId) {
        backlogDebugger.continueBreakpoint(nodeId);
    }

    public Set<String> getSuspendedBreakpointNodeIds() {
        return backlogDebugger.getSuspendedBreakpointNodeIds();
    }

}
