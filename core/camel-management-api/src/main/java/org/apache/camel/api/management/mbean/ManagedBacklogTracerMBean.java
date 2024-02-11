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

import java.util.List;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.spi.BacklogTracerEventMessage;

public interface ManagedBacklogTracerMBean {

    @ManagedAttribute(description = "Camel ID")
    String getCamelId();

    @ManagedAttribute(description = "Camel ManagementName")
    String getCamelManagementName();

    @ManagedAttribute(description = "Is tracing standby")
    boolean isStandby();

    @ManagedAttribute(description = "Is tracing enabled")
    boolean isEnabled();

    @ManagedAttribute(description = "Is tracing enabled")
    void setEnabled(boolean enabled);

    @ManagedAttribute(description = "Number of maximum traced messages in total to keep in the backlog (FIFO queue)")
    int getBacklogSize();

    @ManagedAttribute(description = "Number of maximum traced messages in total to keep in the backlog (FIFO queue)")
    void setBacklogSize(int backlogSize);

    @ManagedAttribute(description = "Whether to remove traced message from backlog when dumping trace messages")
    boolean isRemoveOnDump();

    @ManagedAttribute(description = "Whether to remove traced message from backlog when dumping trace messages")
    void setRemoveOnDump(boolean removeOnDump);

    @ManagedAttribute(description = "To filter tracing by nodes (pattern)")
    void setTracePattern(String pattern);

    @ManagedAttribute(description = "To filter tracing by nodes (pattern)")
    String getTracePattern();

    @ManagedAttribute(description = "To filter tracing by predicate (uses simple language by default)")
    void setTraceFilter(String predicate);

    @ManagedAttribute(description = "To filter tracing by predicate (uses simple language by default)")
    String getTraceFilter();

    @ManagedAttribute(description = "Number of total traced messages")
    long getTraceCounter();

    @ManagedOperation(description = "Resets the trace counter")
    void resetTraceCounter();

    @ManagedAttribute(description = "Number of traced messages in the backlog")
    long getQueueSize();

    @ManagedAttribute(description = "Number of maximum chars in the message body in the trace message. Use zero or negative value to have unlimited size.")
    int getBodyMaxChars();

    @ManagedAttribute(description = "Number of maximum chars in the message body in the trace message. Use zero or negative value to have unlimited size.")
    void setBodyMaxChars(int bodyMaxChars);

    @ManagedAttribute(description = "Whether to include stream based message body in the trace message.")
    boolean isBodyIncludeStreams();

    @ManagedAttribute(description = "Whether to include stream based message body in the trace message.")
    void setBodyIncludeStreams(boolean bodyIncludeStreams);

    @ManagedAttribute(description = "Whether to include file based message body in the trace message.")
    boolean isBodyIncludeFiles();

    @ManagedAttribute(description = "Whether to include file based message body in the trace message.")
    void setBodyIncludeFiles(boolean bodyIncludeFiles);

    @ManagedAttribute(description = "Whether to include exchange properties in the trace message.")
    boolean isIncludeExchangeProperties();

    @ManagedAttribute(description = "Whether to include exchange properties in the trace message.")
    void setIncludeExchangeProperties(boolean includeExchangeProperties);

    @ManagedAttribute(description = "Whether to include exchange variables in the trace message.")
    boolean isIncludeExchangeVariables();

    @ManagedAttribute(description = "Whether to include exchange variables in the trace message.")
    void setIncludeExchangeVariables(boolean includeExchangeVariables);

    @ManagedAttribute(description = "Whether tracing routes created from Rest DSL.")
    boolean isTraceRests();

    @ManagedAttribute(description = "Whether tracing routes created from route templates or kamelets.")
    boolean isTraceTemplates();

    @ManagedOperation(description = "Dumps the traced messages for the given node or route")
    List<BacklogTracerEventMessage> dumpTracedMessages(String nodeOrRouteId);

    @ManagedOperation(description = "Dumps the traced messages for the given node or route in xml format")
    String dumpTracedMessagesAsXml(String nodeOrRouteId);

    @ManagedOperation(description = "Dumps all the traced messages")
    List<BacklogTracerEventMessage> dumpAllTracedMessages();

    @ManagedOperation(description = "Dumps all the traced messages in xml format")
    String dumpAllTracedMessagesAsXml();

    @ManagedOperation(description = "Clears the backlog")
    void clear();

}
