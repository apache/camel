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
 * Error Registry configuration.
 */
@Configurer(extended = true)
public class ErrorRegistryConfigurationProperties implements BootstrapCloseable {

    private MainConfigurationProperties parent;

    @Metadata
    private boolean enabled;
    @Metadata(defaultValue = "100")
    private int maximumEntries = 100;
    @Metadata(defaultValue = "0")
    private int timeToLiveSeconds;
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

    public ErrorRegistryConfigurationProperties(MainConfigurationProperties parent) {
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
     * Whether the error registry is enabled to capture errors during message routing.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaximumEntries() {
        return maximumEntries;
    }

    /**
     * The maximum number of error entries to keep in the registry. When the limit is exceeded, the oldest entries are
     * evicted.
     */
    public void setMaximumEntries(int maximumEntries) {
        this.maximumEntries = maximumEntries;
    }

    public int getTimeToLiveSeconds() {
        return timeToLiveSeconds;
    }

    /**
     * The time-to-live in seconds for error entries. Entries older than this are evicted. The default value is 0
     * (disabled).
     */
    public void setTimeToLiveSeconds(int timeToLiveSeconds) {
        this.timeToLiveSeconds = timeToLiveSeconds;
    }

    public int getBodyMaxChars() {
        return bodyMaxChars;
    }

    /**
     * To limit the message body to a maximum size in the captured error data. Use 0 or negative value to use unlimited
     * size.
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
     * Whether to include the exchange properties in the captured error data.
     */
    public void setIncludeExchangeProperties(boolean includeExchangeProperties) {
        this.includeExchangeProperties = includeExchangeProperties;
    }

    public boolean isIncludeExchangeVariables() {
        return includeExchangeVariables;
    }

    /**
     * Whether to include the exchange variables in the captured error data.
     */
    public void setIncludeExchangeVariables(boolean includeExchangeVariables) {
        this.includeExchangeVariables = includeExchangeVariables;
    }

    // -- fluent builder methods --

    /**
     * Whether the error registry is enabled to capture errors during message routing.
     */
    public ErrorRegistryConfigurationProperties withEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * The maximum number of error entries to keep in the registry. When the limit is exceeded, the oldest entries are
     * evicted.
     */
    public ErrorRegistryConfigurationProperties withMaximumEntries(int maximumEntries) {
        this.maximumEntries = maximumEntries;
        return this;
    }

    /**
     * The time-to-live in seconds for error entries. Entries older than this are evicted.
     */
    public ErrorRegistryConfigurationProperties withTimeToLiveSeconds(int timeToLiveSeconds) {
        this.timeToLiveSeconds = timeToLiveSeconds;
        return this;
    }

    /**
     * To limit the message body to a maximum size in the captured error data. Use 0 or negative value to use unlimited
     * size.
     */
    public ErrorRegistryConfigurationProperties withBodyMaxChars(int bodyMaxChars) {
        this.bodyMaxChars = bodyMaxChars;
        return this;
    }

    /**
     * Whether to include the message body of stream based messages. If enabled then beware the stream may not be
     * re-readable later. See more about Stream Caching.
     */
    public ErrorRegistryConfigurationProperties withBodyIncludeStreams(boolean bodyIncludeStreams) {
        this.bodyIncludeStreams = bodyIncludeStreams;
        return this;
    }

    /**
     * Whether to include the message body of file based messages. The overhead is that the file content has to be read
     * from the file.
     */
    public ErrorRegistryConfigurationProperties withBodyIncludeFiles(boolean bodyIncludeFiles) {
        this.bodyIncludeFiles = bodyIncludeFiles;
        return this;
    }

    /**
     * Whether to include the exchange properties in the captured error data.
     */
    public ErrorRegistryConfigurationProperties withIncludeExchangeProperties(boolean includeExchangeProperties) {
        this.includeExchangeProperties = includeExchangeProperties;
        return this;
    }

    /**
     * Whether to include the exchange variables in the captured error data.
     */
    public ErrorRegistryConfigurationProperties withIncludeExchangeVariables(boolean includeExchangeVariables) {
        this.includeExchangeVariables = includeExchangeVariables;
        return this;
    }
}
