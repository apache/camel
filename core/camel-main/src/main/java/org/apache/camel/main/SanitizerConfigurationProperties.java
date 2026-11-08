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
 * camel-sanitizer configuration: checks during development and tests that the processors of the routes keep the
 * contract of AsyncProcessor (requires camel-sanitizer on the classpath).
 */
@Configurer(extended = true)
public class SanitizerConfigurationProperties implements BootstrapCloseable {

    private MainConfigurationProperties parent;

    @Metadata
    private Boolean enabled;
    @Metadata(defaultValue = "callback")
    private String checks = "callback";
    @Metadata(defaultValue = "32")
    private int stackDepth = 32;
    @Metadata(defaultValue = "true")
    private boolean reportOnShutdown = true;

    public SanitizerConfigurationProperties(MainConfigurationProperties parent) {
        this.parent = parent;
    }

    public MainConfigurationProperties end() {
        return parent;
    }

    @Override
    public void close() {
        parent = null;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * Whether to install the exchange sanitizer. The default is false, and true with profile dev or in a test with the
     * CamelSanitizer annotation. With profile prod the sanitizer stays off, also when this is true.
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getChecks() {
        return checks;
    }

    /**
     * The checks to run, separated by commas: callback (rule R1, the callback of each call of the processor of a step
     * is called exactly once, and process does not throw).
     */
    public void setChecks(String checks) {
        this.checks = checks;
    }

    public int getStackDepth() {
        return stackDepth;
    }

    /**
     * How many frames of each stack a report keeps; 0 keeps only the name of the thread, which costs least.
     */
    public void setStackDepth(int stackDepth) {
        this.stackDepth = stackDepth;
    }

    public boolean isReportOnShutdown() {
        return reportOnShutdown;
    }

    /**
     * Whether to log the report of the sanitizer when the CamelContext stops, if it reported a violation.
     */
    public void setReportOnShutdown(boolean reportOnShutdown) {
        this.reportOnShutdown = reportOnShutdown;
    }

    /**
     * Whether to install the exchange sanitizer. The default is false, and true with profile dev or in a test with the
     * CamelSanitizer annotation. With profile prod the sanitizer stays off, also when this is true.
     */
    public SanitizerConfigurationProperties withEnabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * The checks to run, separated by commas: callback (rule R1, the callback of each call of the processor of a step
     * is called exactly once, and process does not throw).
     */
    public SanitizerConfigurationProperties withChecks(String checks) {
        this.checks = checks;
        return this;
    }

    /**
     * How many frames of each stack a report keeps; 0 keeps only the name of the thread, which costs least.
     */
    public SanitizerConfigurationProperties withStackDepth(int stackDepth) {
        this.stackDepth = stackDepth;
        return this;
    }

    /**
     * Whether to log the report of the sanitizer when the CamelContext stops, if it reported a violation.
     */
    public SanitizerConfigurationProperties withReportOnShutdown(boolean reportOnShutdown) {
        this.reportOnShutdown = reportOnShutdown;
        return this;
    }
}
