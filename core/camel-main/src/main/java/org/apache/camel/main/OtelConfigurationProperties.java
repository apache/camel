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
 * Global configuration for OpenTelemetry
 */
@Configurer(bootstrap = true)
public class OtelConfigurationProperties implements BootstrapCloseable {

    private MainConfigurationProperties parent;

    private boolean enabled;
    @Metadata(defaultValue = "camel", required = true)
    private String instrumentationName = "camel";
    private boolean encoding;
    private String excludePatterns;

    public OtelConfigurationProperties(MainConfigurationProperties parent) {
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
     * To enable OpenTelemetry
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getInstrumentationName() {
        return instrumentationName;
    }

    /**
     * A name uniquely identifying the instrumentation scope, such as the instrumentation library, package, or fully
     * qualified class name. Must not be null.
     */
    public void setInstrumentationName(String instrumentationName) {
        this.instrumentationName = instrumentationName;
    }

    public boolean isEncoding() {
        return encoding;
    }

    /**
     * Sets whether the header keys need to be encoded (connector specific) or not. The value is a boolean. Dashes need
     * for instances to be encoded for JMS property keys.
     */
    public void setEncoding(boolean encoding) {
        this.encoding = encoding;
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

    /**
     * A name uniquely identifying the instrumentation scope, such as the instrumentation library, package, or fully
     * qualified class name. Must not be null.
     */
    public OtelConfigurationProperties withInstrumentationName(String instrumentationName) {
        this.instrumentationName = instrumentationName;
        return this;
    }

    /**
     * To enable OpenTelemetry
     */
    public OtelConfigurationProperties withEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Sets whether the header keys need to be encoded (connector specific) or not. The value is a boolean. Dashes need
     * for instances to be encoded for JMS property keys.
     */
    public OtelConfigurationProperties withEncoding(boolean encoding) {
        this.encoding = encoding;
        return this;
    }

    /**
     * Adds an exclude pattern that will disable tracing for Camel messages that matches the pattern. Multiple patterns
     * can be separated by comma.
     */
    public OtelConfigurationProperties withExcludePatterns(String excludePatterns) {
        this.excludePatterns = excludePatterns;
        return this;
    }

}
