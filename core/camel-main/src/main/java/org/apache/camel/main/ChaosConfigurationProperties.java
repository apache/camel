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
 * camel-chaos configuration: faults turned on in a running application during development (requires camel-chaos on the
 * classpath).
 */
@Configurer(extended = true)
public class ChaosConfigurationProperties implements BootstrapCloseable {

    private MainConfigurationProperties parent;

    @Metadata
    private Boolean enabled;
    @Metadata
    private Long seed;
    @Metadata(security = "insecure:dev")
    private String rules;
    @Metadata(security = "insecure:dev")
    private String scenario;
    @Metadata
    private String reportFile;
    @Metadata(defaultValue = "10m")
    private String maxDuration = "10m";
    @Metadata(defaultValue = "true")
    private boolean reportOnShutdown = true;

    public ChaosConfigurationProperties(MainConfigurationProperties parent) {
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
     * Whether to install the hooks of camel-chaos, which do nothing until a rule is added. The default is false, and
     * true with profile dev. With profile prod chaos stays off, also when this is true.
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Long getSeed() {
        return seed;
    }

    /**
     * The seed of the random draws of the rules. Without it a seed is picked at startup, and shown in the report.
     */
    public void setSeed(Long seed) {
        this.seed = seed;
    }

    public String getRules() {
        return rules;
    }

    /**
     * Rules to add at startup, in the one-line text form of a rule, separated by semicolons.
     */
    public void setRules(String rules) {
        this.rules = rules;
    }

    public String getScenario() {
        return scenario;
    }

    /**
     * A scenario to load at startup: a file with one rule per line, # comments and an optional seed= line (such as
     * chaos/outage.chaos), or classpath:name. Its rules are added before the rules option, and its seed is used unless
     * the seed option is set.
     */
    public void setScenario(String scenario) {
        this.scenario = scenario;
    }

    public String getReportFile() {
        return reportFile;
    }

    /**
     * A file to write the chaos report to as JSON when the CamelContext stops, such as target/chaos-report.json.
     */
    public void setReportFile(String reportFile) {
        this.reportFile = reportFile;
    }

    public String getMaxDuration() {
        return maxDuration;
    }

    /**
     * The longest time a rule runs: a rule without an until duration ends after it, and a longer until duration or
     * delay is rejected when the rule is added.
     */
    public void setMaxDuration(String maxDuration) {
        this.maxDuration = maxDuration;
    }

    public boolean isReportOnShutdown() {
        return reportOnShutdown;
    }

    /**
     * Whether to log the chaos report when the CamelContext stops, if any rule was added.
     */
    public void setReportOnShutdown(boolean reportOnShutdown) {
        this.reportOnShutdown = reportOnShutdown;
    }

    /**
     * Whether to install the hooks of camel-chaos, which do nothing until a rule is added. The default is false, and
     * true with profile dev. With profile prod chaos stays off, also when this is true.
     */
    public ChaosConfigurationProperties withEnabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * The seed of the random draws of the rules. Without it a seed is picked at startup, and shown in the report.
     */
    public ChaosConfigurationProperties withSeed(Long seed) {
        this.seed = seed;
        return this;
    }

    /**
     * Rules to add at startup, in the one-line text form of a rule, separated by semicolons.
     */
    public ChaosConfigurationProperties withRules(String rules) {
        this.rules = rules;
        return this;
    }

    /**
     * A scenario to load at startup: a file with one rule per line, # comments and an optional seed= line (such as
     * chaos/outage.chaos), or classpath:name. Its rules are added before the rules option, and its seed is used unless
     * the seed option is set.
     */
    public ChaosConfigurationProperties withScenario(String scenario) {
        this.scenario = scenario;
        return this;
    }

    /**
     * A file to write the chaos report to as JSON when the CamelContext stops, such as target/chaos-report.json.
     */
    public ChaosConfigurationProperties withReportFile(String reportFile) {
        this.reportFile = reportFile;
        return this;
    }

    /**
     * The longest time a rule runs: a rule without an until duration ends after it, and a longer until duration or
     * delay is rejected when the rule is added.
     */
    public ChaosConfigurationProperties withMaxDuration(String maxDuration) {
        this.maxDuration = maxDuration;
        return this;
    }

    /**
     * Whether to log the chaos report when the CamelContext stops, if any rule was added.
     */
    public ChaosConfigurationProperties withReportOnShutdown(boolean reportOnShutdown) {
        this.reportOnShutdown = reportOnShutdown;
        return this;
    }
}
