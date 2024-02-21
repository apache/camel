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
package org.apache.camel.spi;

import java.util.List;

/**
 * Backlog tracer that captures the last N messages during routing in a backlog.
 */
public interface BacklogTracer {

    /**
     * Is the tracer enabled.
     */
    boolean isEnabled();

    /**
     * To turn on or off the tracer
     */
    void setEnabled(boolean enabled);

    /**
     * Whether the tracer is standby.
     * <p>
     * If a tracer is in standby then the tracer is activated during startup and are ready to be enabled manually via
     * JMX or calling the enabled method.
     */
    boolean isStandby();

    /**
     * Whether the tracer is standby.
     * <p>
     * If a tracer is in standby then the tracer is activated during startup and are ready to be enabled manually via
     * JMX or calling the enabled method.
     */
    void setStandby(boolean standby);

    /**
     * Number of messages to keep in the backlog. Default is 1000.
     */
    int getBacklogSize();

    /**
     * Number of messages to keep in the backlog. Default is 1000.
     */
    void setBacklogSize(int backlogSize);

    /**
     * Remove the currently traced messages when dump methods are invoked
     */
    boolean isRemoveOnDump();

    /**
     * Remove the currently traced messages when dump methods are invoked
     */
    void setRemoveOnDump(boolean removeOnDump);

    /**
     * Maximum number of bytes to keep for the message body (to prevent storing very big payloads)
     */
    int getBodyMaxChars();

    /**
     * Maximum number of bytes to keep for the message body (to prevent storing very big payloads)
     */
    void setBodyMaxChars(int bodyMaxChars);

    /**
     * Trace messages to include message body from streams
     */
    boolean isBodyIncludeStreams();

    /**
     * Trace messages to include message body from streams
     */
    void setBodyIncludeStreams(boolean bodyIncludeStreams);

    /**
     * Trace messages to include message body from files
     */
    boolean isBodyIncludeFiles();

    /**
     * Trace messages to include message body from files
     */
    void setBodyIncludeFiles(boolean bodyIncludeFiles);

    /**
     * Trace messages to include exchange properties
     */
    boolean isIncludeExchangeProperties();

    /**
     * Trace messages to include exchange properties
     */
    void setIncludeExchangeProperties(boolean includeExchangeProperties);

    /**
     * Trace messages to include exchange variables
     */
    boolean isIncludeExchangeVariables();

    /**
     * Trace messages to include exchange variables
     */
    void setIncludeExchangeVariables(boolean includeExchangeVariables);

    /**
     * Trace messages to include exception if the message failed
     */
    boolean isIncludeException();

    /**
     * Trace messages to include exception if the message failed
     */
    void setIncludeException(boolean includeException);

    /**
     * Whether to trace routes that is created from Rest DSL.
     */
    boolean isTraceRests();

    /**
     * Whether to trace routes that is created from route templates or kamelets.
     */
    void setTraceRests(boolean traceRests);

    /**
     * Whether to trace routes that is created from route templates or kamelets.
     */
    boolean isTraceTemplates();

    /**
     * Whether to trace routes that is created from route templates or kamelets.
     */
    void setTraceTemplates(boolean traceTemplates);

    /**
     * Filter for tracing by route or node id
     */
    String getTracePattern();

    /**
     * Filter for tracing by route or node id
     */
    void setTracePattern(String tracePattern);

    /**
     * Filter for tracing messages
     */
    String getTraceFilter();

    /**
     * Filter for tracing messages
     */
    void setTraceFilter(String filter);

    /**
     * Gets the trace counter (total number of traced messages)
     */
    long getTraceCounter();

    /**
     * Number of traced messages in the backlog
     */
    long getQueueSize();

    /**
     * Reset the tracing counter
     */
    void resetTraceCounter();

    /**
     * Dumps all tracing data
     */
    List<BacklogTracerEventMessage> dumpAllTracedMessages();

    /**
     * Dumps tracing data for the given route id / node id
     */
    List<BacklogTracerEventMessage> dumpTracedMessages(String nodeId);

    /**
     * Dumps all tracing data as XML
     */
    String dumpAllTracedMessagesAsXml();

    /**
     * Dumps tracing data for the given route id / node id as XML
     */
    String dumpTracedMessagesAsXml(String nodeId);

    /**
     * Dumps all tracing data as JSon
     */
    String dumpAllTracedMessagesAsJSon();

    /**
     * Dumps tracing data for the given route id / node id as JSon
     */
    String dumpTracedMessagesAsJSon(String nodeId);

    /**
     * Clears the backlog of traced messages.
     */
    void clear();
}
