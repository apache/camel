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

import java.util.List;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;

public interface ManagedTracerBacklogMBean {

    @ManagedAttribute(description = "Is tracing enabled")
    boolean isEnabled();

    @ManagedAttribute(description = "Is tracing enabled")
    void setEnabled(boolean enabled);

    @ManagedAttribute(description = "Number of traced messages to keep in the backlog (FIFO queue)")
    int getBacklogSize();

    @ManagedAttribute(description = "Number of traced messages to keep in the backlog (FIFO queue)")
    void setBacklogSize(int backlogSize);

    @ManagedAttribute(description = "Number of total traced messages")
    long getTraceCounter();

    @ManagedOperation(description = "Resets the trace counter")
    void resetTraceCounter();

    @ManagedOperation(description = "Dumps the traced messages for the given node")
    List<BacklogTracerEventMessage> dumpTracedMessages(String nodeId);

    @ManagedOperation(description = "Dumps the traced messages for the given node in xml format")
    String dumpTracedMessagesAsXml(String nodeId);

    @ManagedOperation(description = "Dumps the traced messages for all nodes")
    List<BacklogTracerEventMessage> dumpAllTracedMessages();

    @ManagedOperation(description = "Dumps the traced messages for all nodes in xml format")
    String dumpAllTracedMessagesAsXml();

}
