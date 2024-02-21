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
package org.apache.camel.main;

import org.apache.camel.LoggingLevel;
import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;

/**
 * Debugger configuration.
 */
@Configurer(bootstrap = true)
public class DebuggerConfigurationProperties implements BootstrapCloseable {

    private MainConfigurationProperties parent;

    @Metadata
    private boolean enabled;
    @Metadata
    private boolean standby;
    @Metadata(label = "advanced")
    private boolean waitForAttach;
    @Metadata(defaultValue = "INFO")
    private LoggingLevel loggingLevel = LoggingLevel.INFO;
    @Metadata
    private String breakpoints;
    @Metadata(label = "advanced")
    private boolean singleStepIncludeStartEnd;
    @Metadata(defaultValue = "131072")
    private int bodyMaxChars = 128 * 1024;
    @Metadata
    private boolean bodyIncludeStreams;
    @Metadata(defaultValue = "true")
    private boolean bodyIncludeFiles = true;
    @Metadata(defaultValue = "true")
    private boolean includeExchangeProperties = true;
    @Metadata(defaultValue = "true")
    private boolean includeExchangeVariables = true;
    @Metadata(defaultValue = "true")
    private boolean includeException = true;
    @Metadata(label = "advanced", defaultValue = "300")
    private long fallbackTimeout = 300;

    public DebuggerConfigurationProperties(MainConfigurationProperties parent) {
        this.parent = parent;
    }

    public MainConfigurationProperties end() {
        return parent;
    }

    @Override
    public void close() {
        parent = null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables Debugger in your Camel application.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isStandby() {
        return standby;
    }

    /**
     * To set the debugger in standby mode, where the debugger will be installed by not automatic enabled. The debugger
     * can then later be enabled explicit from Java, JMX or tooling.
     */
    public void setStandby(boolean standby) {
        this.standby = standby;
    }

    public boolean isWaitForAttach() {
        return waitForAttach;
    }

    /**
     * Whether the debugger should suspend on startup, and wait for a remote debugger to attach. This is what the IDEA
     * and VSCode tooling is using.
     */
    public void setWaitForAttach(boolean waitForAttach) {
        this.waitForAttach = waitForAttach;
    }

    public LoggingLevel getLoggingLevel() {
        return loggingLevel;
    }

    /**
     * The debugger logging level to use when logging activity.
     */
    public void setLoggingLevel(LoggingLevel loggingLevel) {
        this.loggingLevel = loggingLevel;
    }

    public String getBreakpoints() {
        return breakpoints;
    }

    /**
     * Allows to pre-configure breakpoints (node ids) to use with debugger on startup. Multiple ids can be separated by
     * comma. Use special value _all_routes_ to add a breakpoint for the first node for every route, in other words this
     * makes it easy to debug from the beginning of every route without knowing the exact node ids.
     */
    public void setBreakpoints(String breakpoints) {
        this.breakpoints = breakpoints;
    }

    public boolean isSingleStepIncludeStartEnd() {
        return singleStepIncludeStartEnd;
    }

    /**
     * In single step mode, then when the exchange is created and completed, then simulate a breakpoint at start and
     * end, that allows to suspend and watch the incoming/complete exchange at the route (you can see message body as
     * response, failed exception etc).
     */
    public void setSingleStepIncludeStartEnd(boolean singleStepIncludeStartEnd) {
        this.singleStepIncludeStartEnd = singleStepIncludeStartEnd;
    }

    public int getBodyMaxChars() {
        return bodyMaxChars;
    }

    /**
     * To limit the message body to a maximum size in the traced message. Use 0 or negative value to use unlimited size.
     */
    public void setBodyMaxChars(int bodyMaxChars) {
        this.bodyMaxChars = bodyMaxChars;
    }

    public boolean isBodyIncludeStreams() {
        return bodyIncludeStreams;
    }

    /**
     * Whether to include the message body of stream based messages. If enabled then beware the stream may not be
     * re-readable later. See more about Stream Caching.
     */
    public void setBodyIncludeStreams(boolean bodyIncludeStreams) {
        this.bodyIncludeStreams = bodyIncludeStreams;
    }

    public boolean isBodyIncludeFiles() {
        return bodyIncludeFiles;
    }

    /**
     * Whether to include the message body of file based messages. The overhead is that the file content has to be read
     * from the file.
     */
    public void setBodyIncludeFiles(boolean bodyIncludeFiles) {
        this.bodyIncludeFiles = bodyIncludeFiles;
    }

    public boolean isIncludeExchangeProperties() {
        return includeExchangeProperties;
    }

    /**
     * Whether to include the exchange properties in the traced message
     */
    public void setIncludeExchangeProperties(boolean includeExchangeProperties) {
        this.includeExchangeProperties = includeExchangeProperties;
    }

    public boolean isIncludeExchangeVariables() {
        return includeExchangeVariables;
    }

    /**
     * Whether to include the exchange variables in the traced message
     */
    public void setIncludeExchangeVariables(boolean includeExchangeVariables) {
        this.includeExchangeVariables = includeExchangeVariables;
    }

    public boolean isIncludeException() {
        return includeException;
    }

    /**
     * Trace messages to include exception if the message failed
     */
    public void setIncludeException(boolean includeException) {
        this.includeException = includeException;
    }

    public long getFallbackTimeout() {
        return fallbackTimeout;
    }

    /**
     * Fallback Timeout in seconds (300 seconds as default) when block the message processing in Camel. A timeout used
     * for waiting for a message to arrive at a given breakpoint.
     */
    public void setFallbackTimeout(long fallbackTimeout) {
        this.fallbackTimeout = fallbackTimeout;
    }

    /**
     * Enables Debugger in your Camel application.
     */
    public DebuggerConfigurationProperties withEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * To set the debugger in standby mode, where the debugger will be installed by not automatic enabled. The debugger
     * can then later be enabled explicit from Java, JMX or tooling.
     */
    public DebuggerConfigurationProperties withStandby(boolean standby) {
        this.standby = standby;
        return this;
    }

    /**
     * Whether the debugger should suspend on startup, and wait for a remote debugger to attach. This is what the IDEA
     * and VSCode tooling is using.
     */
    public DebuggerConfigurationProperties withWaitForAttach(boolean waitForAttach) {
        this.waitForAttach = waitForAttach;
        return this;
    }

    /**
     * The debugger logging level to use when logging activity.
     */
    public DebuggerConfigurationProperties withLoggingLevel(LoggingLevel loggingLevel) {
        this.loggingLevel = loggingLevel;
        return this;
    }

    /**
     * Allows to pre-configure breakpoints (node ids) to use with debugger on startup. Multiple ids can be separated by
     * comma. Use special value _all_routes_ to add a breakpoint for the first node for every route, in other words this
     * makes it easy to debug from the beginning of every route without knowing the exact node ids.
     */
    public DebuggerConfigurationProperties withBreakpoints(String breakpoints) {
        this.breakpoints = breakpoints;
        return this;
    }

    /**
     * In single step mode, then when the exchange is created and completed, then simulate a breakpoint at start and
     * end, that allows to suspend and watch the incoming/complete exchange at the route (you can see message body as
     * response, failed exception etc).
     */
    public DebuggerConfigurationProperties withSingleStepIncludeStartEnd(boolean singleStepIncludeStartEnd) {
        this.singleStepIncludeStartEnd = singleStepIncludeStartEnd;
        return this;
    }

    /**
     * To limit the message body to a maximum size in the traced message. Use 0 or negative value to use unlimited size.
     */
    public DebuggerConfigurationProperties withBodyMaxChars(int bodyMaxChars) {
        this.bodyMaxChars = bodyMaxChars;
        return this;
    }

    /**
     * Whether to include the message body of stream based messages. If enabled then beware the stream may not be
     * re-readable later. See more about Stream Caching.
     */
    public DebuggerConfigurationProperties withBodyIncludeStreams(boolean bodyIncludeStreams) {
        this.bodyIncludeStreams = bodyIncludeStreams;
        return this;
    }

    /**
     * Whether to include the message body of file based messages. The overhead is that the file content has to be read
     * from the file.
     */
    public DebuggerConfigurationProperties withBodyIncludeFiles(boolean bodyIncludeFiles) {
        this.bodyIncludeFiles = bodyIncludeFiles;
        return this;
    }

    /**
     * Whether to include the exchange properties in the traced message
     */
    public DebuggerConfigurationProperties withIncludeExchangeProperties(boolean includeExchangeProperties) {
        this.includeExchangeProperties = includeExchangeProperties;
        return this;
    }

    /**
     * Whether to include the exchange variables in the traced message
     */
    public DebuggerConfigurationProperties withIncludeExchangeVariables(boolean includeExchangeVariables) {
        this.includeExchangeVariables = includeExchangeVariables;
        return this;
    }

    /**
     * Trace messages to include exception if the message failed
     */
    public DebuggerConfigurationProperties withIncludeException(boolean includeException) {
        this.includeException = includeException;
        return this;
    }

    /**
     * Fallback Timeout in seconds (300 seconds as default) when block the message processing in Camel. A timeout used
     * for waiting for a message to arrive at a given breakpoint.
     */
    public DebuggerConfigurationProperties withFallbackTimeout(long fallbackTimeout) {
        this.fallbackTimeout = fallbackTimeout;
        return this;
    }

}
