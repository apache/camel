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
 * Global configuration for Health Check
 */
@Configurer(bootstrap = true)
public class HealthConfigurationProperties implements BootstrapCloseable {

    private MainConfigurationProperties parent;

    @Metadata(defaultValue = "true")
    private Boolean enabled;
    @Metadata(defaultValue = "true")
    private Boolean routesEnabled;
    @Metadata(defaultValue = "true")
    private Boolean consumersEnabled;
    @Metadata(defaultValue = "false")
    private Boolean producersEnabled;
    @Metadata(defaultValue = "true")
    private Boolean registryEnabled;
    @Metadata
    private String excludePattern;
    @Metadata(enums = "full,default,oneline", defaultValue = "default")
    private String exposureLevel;
    @Metadata(enums = "up,down,unknown", defaultValue = "down")
    private String initialState;

    public HealthConfigurationProperties(MainConfigurationProperties parent) {
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
     * Whether health check is enabled globally
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getRoutesEnabled() {
        return routesEnabled;
    }

    /**
     * Whether routes health check is enabled
     */
    public void setRoutesEnabled(Boolean routesEnabled) {
        this.routesEnabled = routesEnabled;
    }

    public Boolean getConsumersEnabled() {
        return consumersEnabled;
    }

    /**
     * Whether consumers health check is enabled
     */
    public void setConsumersEnabled(Boolean consumersEnabled) {
        this.consumersEnabled = consumersEnabled;
    }

    public Boolean getProducersEnabled() {
        return producersEnabled;
    }

    /**
     * Whether producers health check is enabled
     */
    public void setProducersEnabled(Boolean producersEnabled) {
        this.producersEnabled = producersEnabled;
    }

    public Boolean getRegistryEnabled() {
        return registryEnabled;
    }

    /**
     * Whether registry health check is enabled
     */
    public void setRegistryEnabled(Boolean registryEnabled) {
        this.registryEnabled = registryEnabled;
    }

    public String getExcludePattern() {
        return excludePattern;
    }

    /**
     * Pattern to exclude health checks from being invoked by Camel when checking healths. Multiple patterns can be
     * separated by comma.
     */
    public void setExcludePattern(String excludePattern) {
        this.excludePattern = excludePattern;
    }

    public String getExposureLevel() {
        return exposureLevel;
    }

    /**
     * Sets the level of details to exposure as result of invoking health checks. There are the following levels: full,
     * default, oneline
     *
     * The full level will include all details and status from all the invoked health checks.
     *
     * The default level will report UP if everything is okay, and only include detailed information for health checks
     * that was DOWN.
     *
     * The oneline level will only report either UP or DOWN.
     */
    public void setExposureLevel(String exposureLevel) {
        this.exposureLevel = exposureLevel;
    }

    public String getInitialState() {
        return initialState;
    }

    /**
     * The initial state of health-checks (readiness). There are the following states: UP, DOWN, UNKNOWN.
     *
     * By default, the state is DOWN, is regarded as being pessimistic/careful. This means that the overall health
     * checks may report as DOWN during startup and then only if everything is up and running flip to being UP.
     *
     * Setting the initial state to UP, is regarded as being optimistic. This means that the overall health checks may
     * report as UP during startup and then if a consumer or other service is in fact un-healthy, then the health-checks
     * can flip being DOWN.
     *
     * Setting the state to UNKNOWN means that some health-check would be reported in unknown state, especially during
     * early bootstrap where a consumer may not be fully initialized or validated a connection to a remote system.
     *
     * This option allows to pre-configure the state for different modes.
     */
    public void setInitialState(String initialState) {
        this.initialState = initialState;
    }

    /**
     * Whether health check is enabled globally
     */
    public HealthConfigurationProperties withEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Whether routes health check is enabled
     */
    public HealthConfigurationProperties withRoutesEnabled(boolean routesEnabled) {
        this.routesEnabled = routesEnabled;
        return this;
    }

    /**
     * Whether registry health check is enabled
     */
    public HealthConfigurationProperties withRegistryEnabled(boolean registryEnabled) {
        this.registryEnabled = registryEnabled;
        return this;
    }

    /**
     * Pattern to exclude health checks from being invoked by Camel when checking healths. Multiple patterns can be
     * separated by comma.
     */
    public HealthConfigurationProperties withExcludePattern(String excludePattern) {
        this.excludePattern = excludePattern;
        return this;
    }

    /**
     * Sets the level of details to exposure as result of invoking health checks. There are the following levels: full,
     * default, oneline
     *
     * The full level will include all details and status from all the invoked health checks.
     *
     * The default level will report UP if everything is okay, and only include detailed information for health checks
     * that was DOWN.
     *
     * The oneline level will only report either UP or DOWN.
     */
    public HealthConfigurationProperties withExposureLevel(String exposureLevel) {
        this.exposureLevel = exposureLevel;
        return this;
    }

    /**
     * The initial state of health-checks (readiness). There are the following states: UP, DOWN, UNKNOWN.
     *
     * By default, the state is DOWN, is regarded as being pessimistic/careful. This means that the overall health
     * checks may report as DOWN during startup and then only if everything is up and running flip to being UP.
     *
     * Setting the initial state to UP, is regarded as being optimistic. This means that the overall health checks may
     * report as UP during startup and then if a consumer or other service is in fact un-healthy, then the health-checks
     * can flip being DOWN.
     *
     * Setting the state to UNKNOWN means that some health-check would be reported in unknown state, especially during
     * early bootstrap where a consumer may not be fully initialized or validated a connection to a remote system.
     *
     * This option allows to pre-configure the state for different modes.
     */
    public HealthConfigurationProperties withInitialState(String initialState) {
        this.initialState = initialState;
        return this;
    }

}
