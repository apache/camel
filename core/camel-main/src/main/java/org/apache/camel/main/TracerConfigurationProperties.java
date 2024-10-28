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

import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;

/**
 * Tracer configuration.
 */
@Configurer(bootstrap = true, extended = true)
public class TracerConfigurationProperties implements BootstrapCloseable {

    private MainConfigurationProperties parent;

    @Metadata
    private boolean enabled;
    @Metadata
    private boolean standby;
    @Metadata(label = "advanced", defaultValue = "100")
    private int backlogSize = 100;
    @Metadata(label = "advanced", defaultValue = "true")
    private boolean removeOnDump = true;
    @Metadata(label = "advanced", defaultValue = "32768")
    private int bodyMaxChars = 32 * 1024;
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
    @Metadata(label = "advanced")
    private boolean traceRests;
    @Metadata(label = "advanced")
    private boolean traceTemplates;
    @Metadata
    private String tracePattern;
    @Metadata
    private String traceFilter;

    public TracerConfigurationProperties(MainConfigurationProperties parent) {
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
     * Enables tracer in your Camel application.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isStandby() {
        return standby;
    }

    /**
     * To set the tracer in standby mode, where the tracer will be installed by not automatic enabled. The tracer can
     * then later be enabled explicit from Java, JMX or tooling.
     */
    public void setStandby(boolean standby) {
        this.standby = standby;
    }

    public int getBacklogSize() {
        return backlogSize;
    }

    /**
     * Defines how many of the last messages to keep in the tracer (should be between 1 - 1000).
     */
    public void setBacklogSize(int backlogSize) {
        this.backlogSize = backlogSize;
    }

    public boolean isRemoveOnDump() {
        return removeOnDump;
    }

    /**
     * Whether all traced messages should be removed when the tracer is dumping. By default, the messages are removed,
     * which means that dumping will not contain previous dumped messages.
     */
    public void setRemoveOnDump(boolean removeOnDump) {
        this.removeOnDump = removeOnDump;
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

    public boolean isTraceRests() {
        return traceRests;
    }

    /**
     * Whether to trace routes that is created from Rest DSL.
     */
    public void setTraceRests(boolean traceRests) {
        this.traceRests = traceRests;
    }

    public boolean isTraceTemplates() {
        return traceTemplates;
    }

    /**
     * Whether to trace routes that is created from route templates or kamelets.
     */
    public void setTraceTemplates(boolean traceTemplates) {
        this.traceTemplates = traceTemplates;
    }

    public String getTracePattern() {
        return tracePattern;
    }

    /**
     * Filter for tracing by route or node id
     */
    public void setTracePattern(String tracePattern) {
        this.tracePattern = tracePattern;
    }

    public String getTraceFilter() {
        return traceFilter;
    }

    /**
     * Filter for tracing messages
     */
    public void setTraceFilter(String traceFilter) {
        this.traceFilter = traceFilter;
    }

    /**
     * Enables tracer in your Camel application.
     */
    public TracerConfigurationProperties withEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * To set the tracer in standby mode, where the tracer will be installed by not automatic enabled. The tracer can
     * then later be enabled explicit from Java, JMX or tooling.
     */
    public TracerConfigurationProperties withStandby(boolean standby) {
        this.standby = standby;
        return this;
    }

    /**
     * Defines how many of the last messages to keep in the tracer (should be between 1 - 1000).
     */
    public TracerConfigurationProperties withBacklogSize(int backlogSize) {
        this.backlogSize = backlogSize;
        return this;
    }

    /**
     * Whether all traced messages should be removed when the tracer is dumping. By default, the messages are removed,
     * which means that dumping will not contain previous dumped messages.
     */
    public TracerConfigurationProperties withRemoveOnDump(boolean removeOnDump) {
        this.removeOnDump = removeOnDump;
        return this;
    }

    /**
     * Whether to trace routes that is created from Rest DSL.
     */
    public TracerConfigurationProperties withTraceRests(boolean traceRests) {
        this.traceRests = traceRests;
        return this;
    }

    /**
     * Whether to trace routes that is created from route templates or kamelets.
     */
    public TracerConfigurationProperties withTraceTemplates(boolean traceTemplates) {
        this.traceTemplates = traceTemplates;
        return this;
    }

    /**
     * To limit the message body to a maximum size in the traced message. Use 0 or negative value to use unlimited size.
     */
    public TracerConfigurationProperties withBodyMaxChars(int bodyMaxChars) {
        this.bodyMaxChars = bodyMaxChars;
        return this;
    }

    /**
     * Whether to include the message body of stream based messages. If enabled then beware the stream may not be
     * re-readable later. See more about Stream Caching.
     */
    public TracerConfigurationProperties withBodyIncludeStreams(boolean bodyIncludeStreams) {
        this.bodyIncludeStreams = bodyIncludeStreams;
        return this;
    }

    /**
     * Whether to include the message body of file based messages. The overhead is that the file content has to be read
     * from the file.
     */
    public TracerConfigurationProperties withBodyIncludeFiles(boolean bodyIncludeFiles) {
        this.bodyIncludeFiles = bodyIncludeFiles;
        return this;
    }

    /**
     * Whether to include the exchange properties in the traced message
     */
    public TracerConfigurationProperties withIncludeExchangeProperties(boolean includeExchangeProperties) {
        this.includeExchangeProperties = includeExchangeProperties;
        return this;
    }

    /**
     * Whether to include the exchange variables in the traced message
     */
    public TracerConfigurationProperties withIncludeExchangeVariables(boolean includeExchangeVariables) {
        this.includeExchangeVariables = includeExchangeVariables;
        return this;
    }

    /**
     * Trace messages to include exception if the message failed
     */
    public TracerConfigurationProperties withIncludeException(boolean includeException) {
        this.includeException = includeException;
        return this;
    }

    /**
     * Filter for tracing by route or node id
     */
    public TracerConfigurationProperties withTracePattern(String tracePattern) {
        this.tracePattern = tracePattern;
        return this;
    }

    /**
     * Filter for tracing messages
     */
    public TracerConfigurationProperties withTraceFilter(String traceFilter) {
        this.traceFilter = traceFilter;
        return this;
    }

}
