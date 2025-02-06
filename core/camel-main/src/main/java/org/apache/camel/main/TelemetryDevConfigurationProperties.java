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

/**
 * Global configuration for TelemetryDev component
 */
@Configurer(extended = true)
public class TelemetryDevConfigurationProperties implements BootstrapCloseable {

    private MainConfigurationProperties parent;

    private boolean enabled;
    private String excludePatterns;
    private boolean traceProcessors;
    private String traceFormat;

    public TelemetryDevConfigurationProperties(MainConfigurationProperties parent) {
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
     * To enable TelemetryDev
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getExcludePatterns() {
        return excludePatterns;
    }

    /**
     * Adds an exclude pattern that will disable tracing for Camel messages that matches the pattern. Multiple patterns
     * can be separated by comma.
     */
    public void setExcludePatterns(String excludePatterns) {
        this.excludePatterns = excludePatterns;
    }

    public boolean isTraceProcessors() {
        return traceProcessors;
    }

    /**
     * Setting this to true will create new TelemetrySimple Spans for each Camel Processors. Use the excludePattern
     * property to filter out Processors.
     */
    public void setTraceProcessors(boolean traceProcessors) {
        this.traceProcessors = traceProcessors;
    }

    public String getTraceFormat() {
        return traceFormat;
    }

    /**
     * The output format for traces.
     */
    public void setTraceFormat(String traceFormat) {
        this.traceFormat = traceFormat;
    }

    public TelemetryDevConfigurationProperties withEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public TelemetryDevConfigurationProperties withExcludePatterns(String excludePatterns) {
        this.excludePatterns = excludePatterns;
        return this;
    }

    public TelemetryDevConfigurationProperties withTraceProcessors(boolean traceProcessors) {
        this.traceProcessors = traceProcessors;
        return this;
    }

    public TelemetryDevConfigurationProperties withTraceFromat(String traceFormat) {
        this.traceFormat = traceFormat;
        return this;
    }

}
