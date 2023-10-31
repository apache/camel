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

import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.StatefulService;
import org.apache.camel.util.StopWatch;

import java.util.Set;

/**
 * A {@link org.apache.camel.spi.Debugger} that has easy debugging functionality which can be used from JMX with
 * {@link org.apache.camel.api.management.mbean.ManagedBacklogDebuggerMBean}.
 * <p/>
 * This implementation allows setting breakpoints (with or without a condition) and inspect the {@link Exchange} dumped
 * in XML in {@link BacklogTracerEventMessage} format. There is operations to resume suspended breakpoints to continue
 * routing the {@link Exchange}. There is also step functionality, so you can single step a given {@link Exchange}.
 * <p/>
 * This implementation will only break the first {@link Exchange} that arrives to a breakpoint. If Camel routes using
 * concurrency then sub-sequent {@link Exchange} will continue to be routed, if their breakpoint already holds a
 * suspended {@link Exchange}.
 */
public interface BacklogDebugger extends StatefulService {

    /**
     * Special breakpoint id token to automatically add breakpoint for every first node in every route
     */
    String BREAKPOINT_FIRST_ROUTES = "FIRST_ROUTES";

    String getInitialBreakpoints();

    /**
     * Sets initial breakpoints to set on startup
     */
    void setInitialBreakpoints(String initialBreakpoints);

    String getLoggingLevel();

    void setLoggingLevel(String level);

    void enableDebugger();

    void disableDebugger();

    boolean isEnabled();

    boolean hasBreakpoint(String nodeId);

    void setSuspendMode(boolean suspendMode);

    boolean isSuspendMode();

    boolean isSingleStepMode();

    /**
     * Attach the debugger which will resume the message processing in case the <i>suspend mode</i> is enabled. Do
     * nothing otherwise.
     */
    void attach();

    /**
     * Detach the debugger which will suspend the message processing in case the <i>suspend mode</i> is enabled. Do
     * nothing otherwise.
     */
    void detach();

    void addBreakpoint(String nodeId);

    void addConditionalBreakpoint(String nodeId, String language, String predicate);

    void removeBreakpoint(String nodeId);

    void removeAllBreakpoints();

    Set<String> getBreakpoints();

    void resumeBreakpoint(String nodeId);

    void resumeBreakpoint(String nodeId, boolean stepMode);

    void setMessageBodyOnBreakpoint(String nodeId, Object body);

    void setMessageBodyOnBreakpoint(String nodeId, Object body, Class<?> type);

    void removeMessageBodyOnBreakpoint(String nodeId);

    void setMessageHeaderOnBreakpoint(String nodeId, String headerName, Object value)
            throws NoTypeConversionAvailableException;

    void setMessageHeaderOnBreakpoint(String nodeId, String headerName, Object value, Class<?> type)
            throws NoTypeConversionAvailableException;

    void setExchangePropertyOnBreakpoint(String nodeId, String exchangePropertyName, Object value)
            throws NoTypeConversionAvailableException;

    void setExchangePropertyOnBreakpoint(String nodeId, String exchangePropertyName, Object value, Class<?> type)
            throws NoTypeConversionAvailableException;

    void removeExchangePropertyOnBreakpoint(String nodeId, String exchangePropertyName);

    long getFallbackTimeout();

    void setFallbackTimeout(long fallbackTimeout);

    void removeMessageHeaderOnBreakpoint(String nodeId, String headerName);

    void resumeAll();

    void stepBreakpoint(String nodeId);

    void step();

    Set<String> getSuspendedBreakpointNodeIds();

    /**
     * Gets the trace event for the suspended exchange at the given breakpoint id or null if there is none at that id.
     *
     * @param id node id for the breakpoint
     * @return the trace event or null if there isn't one suspended at the given breakpoint.
     */
    BacklogTracerEventMessage getSuspendedBreakpointMessage(String id);

    void disableBreakpoint(String nodeId);

    void enableBreakpoint(String nodeId);

    int getBodyMaxChars();

    void setBodyMaxChars(int bodyMaxChars);

    boolean isBodyIncludeStreams();

    void setBodyIncludeStreams(boolean bodyIncludeStreams);

    boolean isBodyIncludeFiles();

    void setBodyIncludeFiles(boolean bodyIncludeFiles);

    boolean isIncludeExchangeProperties();

    void setIncludeExchangeProperties(boolean includeExchangeProperties);

    String dumpTracedMessagesAsXml(String nodeId);

    String dumpTracedMessagesAsJSon(String nodeId);

    long getDebugCounter();

    void resetDebugCounter();

    StopWatch beforeProcess(Exchange exchange, Processor processor, NamedNode definition, boolean first);

    void afterProcess(Exchange exchange, Processor processor, NamedNode definition, long timeTaken);

}
