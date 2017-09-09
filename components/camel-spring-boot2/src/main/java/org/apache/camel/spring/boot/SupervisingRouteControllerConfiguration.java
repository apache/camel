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
    /**
     * Global option to enable/disable this ${@link org.apache.camel.spi.RouteController}, default is false.
     */
    private boolean enabled;

    /**
     * Set the amount of time the route controller should wait before to start
     * the routes after the camel context is started or after the route is
     * initialized if the route is created after the camel context is started.
     */
    private String initialDelay;

    /**
     * The default back-off configuration, back-off configuration for routes inherits from this default.
     */
    private BackOffConfiguration defaultBackOff = new BackOffConfiguration();

    /**
     * Routes configuration.
     */
    private Map<String, RouteConfiguration> routes = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getInitialDelay() {
        return initialDelay;
    }

    public void setInitialDelay(String initialDelay) {
        this.initialDelay = initialDelay;
    }

    public BackOffConfiguration getDefaultBackOff() {
        return defaultBackOff;
    }

    public Map<String, RouteConfiguration> getRoutes() {
        return routes;
    }

    // *****************************************
    // Configuration Classes
    // *****************************************

    public static class RouteConfiguration {
        /**
         * Control if the route should be supervised or not, default is true.
         */
        private boolean supervise = true;

        /**
         * The back-off configuration from this route, inherits from default back-off
         */
        private BackOffConfiguration backOff;

        public boolean isSupervised() {
            return supervise;
        }

        public void setSupervise(boolean supervise) {
            this.supervise = supervise;
        }

        public BackOffConfiguration getBackOff() {
            return backOff;
        }

        public void setBackOff(BackOffConfiguration backOff) {
            this.backOff = backOff;
        }
    }

    public static class BackOffConfiguration {
        /**
         * The delay to wait before retry the operation.
         *
         * You can also specify time values using units, such as 60s (60 seconds),
         * 5m30s (5 minutes and 30 seconds), and 1h (1 hour).
         */
        private String delay;

        /**
         * The maximum back-off time.
         *
         * You can also specify time values using units, such as 60s (60 seconds),
         * 5m30s (5 minutes and 30 seconds), and 1h (1 hour).
         */
        private String maxDelay;

        /**
         * The maximum elapsed time after which the back-off is exhausted.
         *
         * You can also specify time values using units, such as 60s (60 seconds),
         * 5m30s (5 minutes and 30 seconds), and 1h (1 hour).
         */
        private String maxElapsedTime;

        /**
         * The maximum number of attempts after which the back-off is exhausted.
         */
        private Long maxAttempts;

        /**
         * The value to multiply the current interval by for each retry attempt.
         */
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
