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

import org.apache.camel.spi.Configurer;

/**
 * Global configuration for Health Check
 */
@Configurer
public class HealthConfigurationProperties {

    private final MainConfigurationProperties parent;

    private Boolean enabled;
    private Boolean contextEnabled;
    private Boolean routesEnabled;
    private Boolean registryEnabled;
    private Map<String, HealthConfiguration> config = new HashMap<>();

    public HealthConfigurationProperties(MainConfigurationProperties parent) {
        this.parent = parent;
    }

    public MainConfigurationProperties end() {
        return parent;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getContextEnabled() {
        return contextEnabled;
    }

    public void setContextEnabled(Boolean contextEnabled) {
        this.contextEnabled = contextEnabled;
    }

    public Boolean getRoutesEnabled() {
        return routesEnabled;
    }

    public void setRoutesEnabled(Boolean routesEnabled) {
        this.routesEnabled = routesEnabled;
    }

    public Boolean getRegistryEnabled() {
        return registryEnabled;
    }

    public void setRegistryEnabled(Boolean registryEnabled) {
        this.registryEnabled = registryEnabled;
    }

    public Map<String, HealthConfiguration> getConfig() {
        return config;
    }

    public void setConfig(Map<String, HealthConfiguration> config) {
        this.config = config;
    }

    // TODO: Fluent builder

    @Configurer
    public static class HealthConfiguration {

        private Boolean enabled;
        private Long interval;
        private Integer failureThreshold;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Long getInterval() {
            return interval;
        }

        public void setInterval(Long interval) {
            this.interval = interval;
        }

        public Integer getFailureThreshold() {
            return failureThreshold;
        }

        public void setFailureThreshold(Integer failureThreshold) {
            this.failureThreshold = failureThreshold;
        }
    }
}
