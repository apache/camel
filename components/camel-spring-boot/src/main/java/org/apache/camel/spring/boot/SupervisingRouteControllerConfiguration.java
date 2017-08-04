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
package org.apache.camel.spring.boot;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "camel.supervising.controller")
public class SupervisingRouteControllerConfiguration {
    private boolean enabled;
    private BackOffConfiguration backOff = new BackOffConfiguration();
    private Map<String, RouteConfiguration> routes = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public BackOffConfiguration getBackOff() {
        return backOff;
    }

    public Map<String, RouteConfiguration> getRoutes() {
        return routes;
    }

    // *****************************************
    // Configuration Classes
    // *****************************************

    public static class RouteConfiguration {
        private BackOffConfiguration backOff;

        public BackOffConfiguration getBackOff() {
            return backOff;
        }

        public void setBackOff(BackOffConfiguration backOff) {
            this.backOff = backOff;
        }
    }

    public static class BackOffConfiguration {
        private String delay;
        private String maxDelay;
        private String maxElapsedTime;
        private Long maxAttempts;
        private Double multiplier;

        public String getDelay() {
            return delay;
        }

        public void setDelay(String delay) {
            this.delay = delay;
        }

        public String getMaxDelay() {
            return maxDelay;
        }

        public void setMaxDelay(String maxDelay) {
            this.maxDelay = maxDelay;
        }

        public String getMaxElapsedTime() {
            return maxElapsedTime;
        }

        public void setMaxElapsedTime(String maxElapsedTime) {
            this.maxElapsedTime = maxElapsedTime;
        }

        public Long getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(Long maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Double getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(Double multiplier) {
            this.multiplier = multiplier;
        }
    }
}
