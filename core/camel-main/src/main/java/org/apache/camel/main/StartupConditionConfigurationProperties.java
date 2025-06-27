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
 * Configuration for startup conditions
 */
@Configurer(extended = true)
public class StartupConditionConfigurationProperties implements BootstrapCloseable {

    private MainConfigurationProperties parent;

    @Metadata(defaultValue = "false")
    private boolean enabled;
    @Metadata(defaultValue = "500")
    private int interval = 500;
    @Metadata(defaultValue = "20000")
    private int timeout = 20000;
    @Metadata(defaultValue = "stop", enums = "fail,stop,ignore")
    private String onTimeout = "stop";
    @Metadata
    private String environmentVariableExists;
    @Metadata
    private String fileExists;
    @Metadata
    private String customClassNames;

    public StartupConditionConfigurationProperties(MainConfigurationProperties parent) {
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
     * To enable using startup conditions
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getInterval() {
        return interval;
    }

    /**
     * Interval in millis between checking conditions.
     */
    public void setInterval(int interval) {
        this.interval = interval;
    }

    public int getTimeout() {
        return timeout;
    }

    /**
     * Total timeout (in millis) for all startup conditions.
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public String getOnTimeout() {
        return onTimeout;
    }

    /**
     * What action, to do on timeout.
     *
     * fail = do not startup, and throw an exception causing camel to fail stop = do not startup, and stop camel ignore
     * = log a WARN and continue to startup
     */
    public void setOnTimeout(String onTimeout) {
        this.onTimeout = onTimeout;
    }

    public String getEnvironmentVariableExists() {
        return environmentVariableExists;
    }

    /**
     * Wait for an environment variable with the given name to exists before continuing
     */
    public void setEnvironmentVariableExists(String environmentVariableExists) {
        this.environmentVariableExists = environmentVariableExists;
    }

    public String getFileExists() {
        return fileExists;
    }

    /**
     * Wait for a file with the given name to exists before continuing
     */
    public void setFileExists(String fileExists) {
        this.fileExists = fileExists;
    }

    public String getCustomClassNames() {
        return customClassNames;
    }

    /**
     * A list of custom class names (FQN). Multiple classes can be separated by comma.
     */
    public void setCustomClassNames(String customClassNames) {
        this.customClassNames = customClassNames;
    }

    /**
     * To enable using startup conditions
     */
    public StartupConditionConfigurationProperties withEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Interval in millis between checking startup conditions
     */
    public StartupConditionConfigurationProperties withInterval(int interval) {
        this.interval = interval;
        return this;
    }

    /**
     * Total timeout (in millis) for all startup conditions.
     */
    public StartupConditionConfigurationProperties withTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * What action, to do on timeout.
     *
     * fail = do not startup, and throw an exception causing camel to fail stop = do not startup, and stop camel ignore
     * = log a WARN and continue to startup
     */
    public StartupConditionConfigurationProperties withOnTimeout(String onTimeout) {
        this.onTimeout = onTimeout;
        return this;
    }

    /**
     * Wait for an environment variable with the given name to exists before continuing
     */
    public StartupConditionConfigurationProperties withEnvironmentVariableExists(String environmentVariableExists) {
        this.environmentVariableExists = environmentVariableExists;
        return this;
    }

    /**
     * Wait for a file with the given name to exists before continuing
     */
    public StartupConditionConfigurationProperties withFileExists(String fileExists) {
        this.fileExists = fileExists;
        return this;
    }

    /**
     * A list of custom class names (FQN). Multiple classes can be separated by comma.
     */
    public StartupConditionConfigurationProperties withCustomClassNames(String customClassNames) {
        this.customClassNames = customClassNames;
        return this;
    }

}
