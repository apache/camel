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
 * contract of AsyncProcessor, and that the commits of the consumers run exactly once (requires camel-sanitizer on the
 * classpath).
 */
@Configurer(extended = true)
public class SanitizerConfigurationProperties implements BootstrapCloseable {

    private MainConfigurationProperties parent;

    @Metadata
    private Boolean enabled;
    @Metadata(defaultValue = "callback,threads")
    private String checks;
    @Metadata(defaultValue = "32")
    private int stackDepth = 32;
    @Metadata(defaultValue = "0")
    private Integer overlapSampling;
    @Metadata(defaultValue = "1ms")
    private String overlapWindow = "1ms";
    @Metadata(defaultValue = "60s")
    private String pendingTimeout = "60s";
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
     * is called exactly once, and process does not throw), commit (rule R2, each commit of a consumer runs exactly
     * once, for the unit of work that owns it) and threads (rule R3, process returns the value its callback is called
     * with). The default is callback,threads, as rule R2 costs more per message; for a CamelContext built on a thread
     * the JUnit extension marked (a test with the CamelSanitizer annotation, or the CI switch) it is
     * callback,commit,threads.
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

    public Integer getOverlapSampling() {
        return overlapSampling;
    }

    /**
     * Rule R3 (in checks threads): a short watch window is opened at every Nth point where the routing engine uses an
     * exchange (a step is called, the callback of a step continues the exchange, an on completion runs), and its thread
     * pauses in it; another thread that uses the same exchange at such a point during the window is reported with it.
     * The points are counted, not drawn at random, so a rerun samples the same points of the same run. 0 is off, the
     * default, as a paused thread changes the timing of the application; for a CamelContext built on a thread the JUnit
     * extension marked (a test with the CamelSanitizer annotation, or the CI switch) it is 100, and a test class turns
     * it off for itself with CamelSanitizer(overlapSampling = 0).
     */
    public void setOverlapSampling(Integer overlapSampling) {
        this.overlapSampling = overlapSampling;
    }

    public String getOverlapWindow() {
        return overlapWindow;
    }

    /**
     * How long a thread pauses in a watch window of rule R3 (see overlapSampling), such as 1ms or 5 (milliseconds);
     * more than 0. The pause uses the system clock, not the TimeSource of the context, so a virtual clock does not
     * stretch it.
     */
    public void setOverlapWindow(String overlapWindow) {
        this.overlapWindow = overlapWindow;
    }

    public String getPendingTimeout() {
        return pendingTimeout;
    }

    /**
     * How long an exchange waits, with nothing happening in it, before the call of a step whose callback has not come
     * is reported as still pending (a warning of rule R1, which never fails a test), such as 60s or 500 (milliseconds).
     * Only the innermost pending call of an exchange is reported. 0 turns the warning off, as does a value of checks
     * without callback. The parts of a split, multicast or recipient list with parallel processing share one lock of
     * the table of pending calls, which orders their threads: when hunting races between such parts, use 0, and leave
     * commit out of checks (checks=callback,threads), as rule R2 takes one lock of the context at each unit of work
     * given, each on completion added, handed over or run, and each exchange done.
     */
    public void setPendingTimeout(String pendingTimeout) {
        this.pendingTimeout = pendingTimeout;
    }

    public boolean isReportOnShutdown() {
        return reportOnShutdown;
    }

    /**
     * Whether to log the report of the sanitizer when the CamelContext stops, if it reported a violation or a still
     * pending warning, and the calls of steps still pending then.
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
     * is called exactly once, and process does not throw), commit (rule R2, each commit of a consumer runs exactly
     * once, for the unit of work that owns it) and threads (rule R3, process returns the value its callback is called
     * with). The default is callback,threads, as rule R2 costs more per message; for a CamelContext built on a thread
     * the JUnit extension marked (a test with the CamelSanitizer annotation, or the CI switch) it is
     * callback,commit,threads.
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
     * Rule R3 (in checks threads): a short watch window is opened at every Nth point where the routing engine uses an
     * exchange (a step is called, the callback of a step continues the exchange, an on completion runs), and its thread
     * pauses in it; another thread that uses the same exchange at such a point during the window is reported with it.
     * The points are counted, not drawn at random, so a rerun samples the same points of the same run. 0 is off, the
     * default, as a paused thread changes the timing of the application; for a CamelContext built on a thread the JUnit
     * extension marked (a test with the CamelSanitizer annotation, or the CI switch) it is 100, and a test class turns
     * it off for itself with CamelSanitizer(overlapSampling = 0).
     */
    public SanitizerConfigurationProperties withOverlapSampling(Integer overlapSampling) {
        this.overlapSampling = overlapSampling;
        return this;
    }

    /**
     * How long a thread pauses in a watch window of rule R3 (see overlapSampling), such as 1ms or 5 (milliseconds);
     * more than 0. The pause uses the system clock, not the TimeSource of the context, so a virtual clock does not
     * stretch it.
     */
    public SanitizerConfigurationProperties withOverlapWindow(String overlapWindow) {
        this.overlapWindow = overlapWindow;
        return this;
    }

    /**
     * How long an exchange waits, with nothing happening in it, before the call of a step whose callback has not come
     * is reported as still pending (a warning of rule R1, which never fails a test), such as 60s or 500 (milliseconds).
     * Only the innermost pending call of an exchange is reported. 0 turns the warning off, as does a value of checks
     * without callback. The parts of a split, multicast or recipient list with parallel processing share one lock of
     * the table of pending calls, which orders their threads: when hunting races between such parts, use 0, and leave
     * commit out of checks (checks=callback,threads), as rule R2 takes one lock of the context at each unit of work
     * given, each on completion added, handed over or run, and each exchange done.
     */
    public SanitizerConfigurationProperties withPendingTimeout(String pendingTimeout) {
        this.pendingTimeout = pendingTimeout;
        return this;
    }

    /**
     * Whether to log the report of the sanitizer when the CamelContext stops, if it reported a violation or a still
     * pending warning, and the calls of steps still pending then.
     */
    public SanitizerConfigurationProperties withReportOnShutdown(boolean reportOnShutdown) {
        this.reportOnShutdown = reportOnShutdown;
        return this;
    }
}
