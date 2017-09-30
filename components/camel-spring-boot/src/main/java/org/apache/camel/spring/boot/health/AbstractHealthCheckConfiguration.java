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
package org.apache.camel.spring.boot.health;

import org.apache.camel.health.HealthCheckConfiguration;

public abstract class AbstractHealthCheckConfiguration {
    /**
     * Set if the check associated to this configuration is enabled or not.
     */
    private Boolean enabled;

    /**
     * Set the check interval.
     */
    private String interval;

    /**
     * Set the number of failure before reporting the service as un-healthy.
     */
    private Integer failureThreshold;


    /**
     * @return true if the check associated to this configuration is enabled,
     * false otherwise.
     */
    public Boolean isEnabled() {
        return enabled;
    }

    /**
     * Set if the check associated to this configuration is enabled or not.
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return the check interval.
     */
    public String getInterval() {
        return interval;
    }

    /**
     * Set the check interval.
     */
    public void setInterval(String interval) {
        this.interval = interval;
    }

    /**
     * @return the number of failure before reporting the service as un-healthy.
     */
    public Integer getFailureThreshold() {
        return failureThreshold;
    }

    /**
     * Set the number of failure before reporting the service as un-healthy.
     */
    public void setFailureThreshold(Integer failureThreshold) {
        this.failureThreshold = failureThreshold;
    }

    /**
     * Convert this configuration to a {@link HealthCheckConfiguration} using default values.
     */
    public HealthCheckConfiguration asHealthCheckConfiguration() {
        return HealthCheckConfiguration.builder()
            .enabled(this.isEnabled())
            .interval(this.getInterval())
            .failureThreshold(this.getFailureThreshold())
            .build();
    }
}
