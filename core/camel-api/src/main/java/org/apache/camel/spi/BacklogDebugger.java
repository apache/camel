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

import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.StatefulService;
import org.apache.camel.util.StopWatch;

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
     * The name of the OS environment variable that contains the value of the flag indicating whether the
     * {@code BacklogDebugger} should suspend processing the messages and wait for a debugger to attach or not.
     */
    String SUSPEND_MODE_ENV_VAR_NAME = "CAMEL_DEBUGGER_SUSPEND";

    /**
     * The name of the system property that contains the value of the flag indicating whether the
     * {@code BacklogDebugger} should suspend processing the messages and wait for a debugger to attach or not.
     */
    String SUSPEND_MODE_SYSTEM_PROP_NAME = "org.apache.camel.debugger.suspend";

    /**
     * Special breakpoint id token to automatically add breakpoint for every route.
     */
    String BREAKPOINT_ALL_ROUTES = "_all_routes_";

    /**
     * Allows to pre-configure breakpoints (node ids) to use with debugger on startup. Multiple ids can be separated by
     * comma. Use special value _all_routes_ to add a breakpoint for the first node for every route, in other words this
     * makes it easy to debug from the beginning of every route without knowing the exact node ids.
     */
    String getInitialBreakpoints();

    /**
     * Allows to pre-configure breakpoints (node ids) to use with debugger on startup. Multiple ids can be separated by
     * comma. Use special value _all_routes_ to add a breakpoint for the first node for every route, in other words this
     * makes it easy to debug from the beginning of every route without knowing the exact node ids.
     */
    void setInitialBreakpoints(String initialBreakpoints);

    /**
     * The debugger logging level to use when logging activity.
     */
    String getLoggingLevel();

    /**
     * The debugger logging level to use when logging activity.
     */
    void setLoggingLevel(String level);

    /**
     * To enable the debugger
     */
    void enableDebugger();

    /**
     * To disable the debugger
     */
    void disableDebugger();

    /**
     * Whether the debugger is enabled
     */
    boolean isEnabled();

    /**
     * Whether the debugger is standby.
     * <p>
     * If a debugger is in standby then the tracer is activated during startup and are ready to be enabled manually via
     * JMX or calling the enableDebugger method.
     */
    boolean isStandby();

    /**
     * Whether the debugger is standby.
     * <p>
     * If a debugger is in standby then the tracer is activated during startup and are ready to be enabled manually via
     * JMX or calling the enableDebugger method.
     */
    void setStandby(boolean standby);

    /**
     * Does the node have a breakpoint
     */
    boolean hasBreakpoint(String nodeId);

    /**
     * Whether the debugger should suspend on startup, and wait for a remote debugger to attach. This is what the IDEA
     * and VSCode tooling is using.
     */
    void setSuspendMode(boolean suspendMode);

    /**
     * Whether the debugger should suspend on startup, and wait for a remote debugger to attach. This is what the IDEA
     * and VSCode tooling is using.
     */
    boolean isSuspendMode();

    /**
     * Is the debugger currently in single step mode
     */
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

    /**
     * Adds a breakpoint for the given node
     */
    void addBreakpoint(String nodeId);

    /**
     * Adds a conditional breakpoint for the given node
     */
    void addConditionalBreakpoint(String nodeId, String language, String predicate);

    /**
     * Removes the breakpoint
     */
    void removeBreakpoint(String nodeId);

    /**
     * Remove all breakpoints
     */
    void removeAllBreakpoints();

    /**
     * Gets all the breakpoint (node ids)
     */
    Set<String> getBreakpoints();

    /**
     * Resume the breakpoint
     */
    void resumeBreakpoint(String nodeId);

    /**
     * Resume the breakpoint in step mode
     */
    void resumeBreakpoint(String nodeId, boolean stepMode);

    /**
     * Updates the message body at the given breakpoint
     */
    void setMessageBodyOnBreakpoint(String nodeId, Object body);

    /**
     * Updates the message body at the given breakpoint
     */
    void setMessageBodyOnBreakpoint(String nodeId, Object body, Class<?> type);

    /**
     * Removes the message body (set as null) at the given breakpoint
     */
    void removeMessageBodyOnBreakpoint(String nodeId);

    /**
     * Sets the message header at the given breakpoint
     */
    void setMessageHeaderOnBreakpoint(String nodeId, String headerName, Object value)
            throws NoTypeConversionAvailableException;

    /**
     * Sets the message header at the given breakpoint
     */
    void setMessageHeaderOnBreakpoint(String nodeId, String headerName, Object value, Class<?> type)
            throws NoTypeConversionAvailableException;

    /**
     * Sets the exchange property at the given breakpoint
     */
    void setExchangePropertyOnBreakpoint(String nodeId, String exchangePropertyName, Object value)
            throws NoTypeConversionAvailableException;

    /**
     * Updates the exchange property at the given breakpoint
     */
    void setExchangePropertyOnBreakpoint(String nodeId, String exchangePropertyName, Object value, Class<?> type)
            throws NoTypeConversionAvailableException;

    /**
     * Removes the message header at the given breakpoint
     */
    void removeMessageHeaderOnBreakpoint(String nodeId, String headerName);

    /**
     * Removes the exchange property at the given breakpoint
     */
    void removeExchangePropertyOnBreakpoint(String nodeId, String exchangePropertyName);

    /**
     * Updates/adds the variable (uses same type as old variableName value) on the suspended breakpoint at the given
     * node id
     */
    void setExchangeVariableOnBreakpoint(String nodeId, String variableName, Object value)
            throws NoTypeConversionAvailableException;

    /**
     * Updates/adds the variable (with a new type) on the suspended breakpoint at the given node id
     */
    void setExchangeVariableOnBreakpoint(String nodeId, String variableName, Object value, Class<?> type)
            throws NoTypeConversionAvailableException;

    /**
     * Removes the variable on the suspended breakpoint at the given node id
     */
    void removeExchangeVariableOnBreakpoint(String nodeId, String variableName);

    /**
     * Fallback Timeout in seconds (300 seconds as default) when block the message processing in Camel. A timeout used
     * for waiting for a message to arrive at a given breakpoint.
     */
    long getFallbackTimeout();

    /**
     * Fallback Timeout in seconds (300 seconds as default) when block the message processing in Camel. A timeout used
     * for waiting for a message to arrive at a given breakpoint.
     */
    void setFallbackTimeout(long fallbackTimeout);

    /**
     * To resume all suspended breakpoints.
     */
    void resumeAll();

    /**
     * To start single step mode from a suspended breakpoint at the given node. Then invoke {@link #step()} to step to
     * next node in the route.
     */
    void stepBreakpoint(String nodeId);

    /**
     * To start single step mode from the current suspended breakpoint. Then invoke {@link #step()} to step to next node
     * in the route.
     */
    void stepBreakpoint();

    /**
     * To step to next node when in single step mode.
     */
    void step();

    /**
     * Gets node ids for all current suspended exchanges at breakpoints
     */
    Set<String> getSuspendedBreakpointNodeIds();

    /**
     * Gets the exchanged suspended at the given breakpoint id or null if there is none at that id.
     *
     * @param  id node id for the breakpoint
     * @return    the suspended exchange or null if there isn't one suspended at the given breakpoint.
     */
    Exchange getSuspendedExchange(String id);

    /**
     * Gets the trace event for the suspended exchange at the given breakpoint id or null if there is none at that id.
     *
     * @param  id node id for the breakpoint
     * @return    the trace event or null if there isn't one suspended at the given breakpoint.
     */
    BacklogTracerEventMessage getSuspendedBreakpointMessage(String id);

    /**
     * Disables a breakpoint
     */
    void disableBreakpoint(String nodeId);

    /**
     * Enables a breakpoint
     */
    void enableBreakpoint(String nodeId);

    /**
     * In single step mode, then when the exchange is created and completed, then simulate a breakpoint at start and
     * end, that allows to suspend and watch the incoming/complete exchange at the route (you can see message body as
     * response, failed exception etc).
     */
    void setSingleStepIncludeStartEnd(boolean singleStepIncludeStartEnd);

    /**
     * In single step mode, then when the exchange is created and completed, then simulate a breakpoint at start and
     * end, that allows to suspend and watch the incoming/complete exchange at the route (you can see message body as
     * response, failed exception etc).
     */
    boolean isSingleStepIncludeStartEnd();

    /**
     * To limit the message body to a maximum size in the traced message. Use 0 or negative value to use unlimited size.
     */
    int getBodyMaxChars();

    /**
     * To limit the message body to a maximum size in the traced message. Use 0 or negative value to use unlimited size.
     */
    void setBodyMaxChars(int bodyMaxChars);

    /**
     * Whether to include the message body of stream based messages. If enabled then beware the stream may not be
     * re-readable later. See more about Stream Caching.
     */
    boolean isBodyIncludeStreams();

    /**
     * Whether to include the message body of stream based messages. If enabled then beware the stream may not be
     * re-readable later. See more about Stream Caching.
     */
    void setBodyIncludeStreams(boolean bodyIncludeStreams);

    /**
     * Whether to include the message body of file based messages. The overhead is that the file content has to be read
     * from the file.
     */
    boolean isBodyIncludeFiles();

    /**
     * Whether to include the message body of file based messages. The overhead is that the file content has to be read
     * from the file.
     */
    void setBodyIncludeFiles(boolean bodyIncludeFiles);

    /**
     * Whether to include the exchange properties in the traced message
     */
    boolean isIncludeExchangeProperties();

    /**
     * Whether to include the exchange properties in the traced message
     */
    void setIncludeExchangeProperties(boolean includeExchangeProperties);

    /**
     * Whether to include the exchange variables in the traced message
     */
    boolean isIncludeExchangeVariables();

    /**
     * Whether to include the exchange variables in the traced message
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
     * To dump the debugged messages from the give node id in XML format.
     */
    String dumpTracedMessagesAsXml(String nodeId);

    /**
     * To dump the debugged messages from the give node id in JSon format.
     */
    String dumpTracedMessagesAsJSon(String nodeId);

    /**
     * Number of breakpoint that has been hit
     */
    long getDebugCounter();

    /**
     * Rests the debug counter
     */
    void resetDebugCounter();

    /**
     * Callback invoked before hitting a breakpoint
     */
    StopWatch beforeProcess(Exchange exchange, Processor processor, NamedNode definition);

    /**
     * Callback invoked after a breakpoint
     */
    void afterProcess(Exchange exchange, Processor processor, NamedNode definition, long timeTaken);

}
