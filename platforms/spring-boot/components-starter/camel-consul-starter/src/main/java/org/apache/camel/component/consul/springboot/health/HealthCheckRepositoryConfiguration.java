/**
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
package org.apache.camel.component.consul.springboot.health;

import java.util.List;
import java.util.Map;

import org.apache.camel.spring.boot.health.AbstractHealthCheckConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("camel.component.consul.health.check.repository")
public class HealthCheckRepositoryConfiguration extends AbstractHealthCheckConfiguration {
    /**
     * Define the checks to include.
     */
    private List<String> checks;

    /**
     * Health check configurations.
     */
    private Map<String, AbstractHealthCheckConfiguration> configurations;

    // ******************************
    // Properties
    // ******************************

    public List<String> getChecks() {
        return checks;
    }

    public void setChecks(List<String> checks) {
        this.checks = checks;
    }

    public Map<String, AbstractHealthCheckConfiguration> getConfigurations() {
        return configurations;
    }

    public void setConfigurations(Map<String, AbstractHealthCheckConfiguration> configurations) {
        this.configurations = configurations;
    }
}