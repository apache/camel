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
package org.apache.camel.component.consul.health;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.consul.ConsulClientConfiguration;
import org.apache.camel.health.HealthCheckConfiguration;

public class ConsulHealthCheckRepositoryConfiguration extends ConsulClientConfiguration {
    /**
     * Default configuration.
     */
    private HealthCheckConfiguration defaultConfiguration;

    /**
     * Define the checks to include.
     */
    private Set<String> checks;

    /**
     * Service configuration.
     */
    private Map<String, HealthCheckConfiguration> configurations;

    public ConsulHealthCheckRepositoryConfiguration() {
        this.checks = new HashSet<>();
        this.configurations = new HashMap<>();
    }

    // ****************************************
    // Properties
    // ****************************************

    public HealthCheckConfiguration getDefaultConfiguration() {
        return defaultConfiguration;
    }

    public void setDefaultConfiguration(HealthCheckConfiguration defaultConfiguration) {
        this.defaultConfiguration = defaultConfiguration;
    }

    public Set<String> getChecks() {
        return checks;
    }

    public void addCheck(String id) {
        checks.add(id);
    }

    public Map<String, HealthCheckConfiguration> getConfigurations() {
        return configurations;
    }

    public void addConfiguration(String id, HealthCheckConfiguration configuration) {
        if (this.configurations == null) {
            this.configurations = new HashMap<>();
        }

        this.configurations.put(id, configuration);
    }

    // ****************************************
    // Copy
    // ****************************************

    @Override
    public ConsulHealthCheckRepositoryConfiguration copy() {
        try {
            return (ConsulHealthCheckRepositoryConfiguration)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
