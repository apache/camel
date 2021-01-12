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

import java.util.HashMap;
import java.util.Map;

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
    private Boolean contextEnabled;
    @Metadata(defaultValue = "true")
    private Boolean routesEnabled;
    @Metadata(defaultValue = "true")
    private Boolean registryEnabled;
    private Map<String, HealthCheckConfigurationProperties> config = new HashMap<>();

    public HealthConfigurationProperties(MainConfigurationProperties parent) {
        this.parent = parent;
    }

    public MainConfigurationProperties end() {
        return parent;
    }

    @Override
    public void close() {
        config.clear();
        config = null;
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

    public Boolean getContextEnabled() {
        return contextEnabled;
    }

    /**
     * Whether context health check is enabled
     */
    public void setContextEnabled(Boolean contextEnabled) {
        this.contextEnabled = contextEnabled;
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

    public Boolean getRegistryEnabled() {
        return registryEnabled;
    }

    /**
     * Whether registry health check is enabled
     */
    public void setRegistryEnabled(Boolean registryEnabled) {
        this.registryEnabled = registryEnabled;
    }

    public Map<String, HealthCheckConfigurationProperties> getConfig() {
        return config;
    }

    /**
     * Set additional {@link HealthConfigurationProperties} for fine grained configuration of health checks.
     */
    public void setConfig(Map<String, HealthCheckConfigurationProperties> config) {
        this.config = config;
    }

    /**
     * Whether health check is enabled globally
     */
    public HealthConfigurationProperties withEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Whether context health check is enabled
     */
    public HealthConfigurationProperties withContextEnabled(boolean contextEnabled) {
        this.contextEnabled = contextEnabled;
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
     * Additional {@link HealthConfigurationProperties} for fine grained configuration of health checks.
     */
    public HealthConfigurationProperties addConfig(String id, HealthCheckConfigurationProperties config) {
        this.config.put(id, config);
        return this;
    }

}
