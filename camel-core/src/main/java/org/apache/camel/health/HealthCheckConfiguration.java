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
package org.apache.camel.health;

import java.time.Duration;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.converter.TimePatternConverter;
import org.apache.camel.util.ObjectHelper;

public class HealthCheckConfiguration implements Cloneable {
    public static final Boolean DEFAULT_VALUE_ENABLED = Boolean.FALSE;
    public static final Duration DEFAULT_VALUE_INTERVAL = Duration.ZERO;
    public static final Integer DEFAULT_VALUE_FAILURE_THRESHOLD = 0;

    /**
     * Set if the check associated to this configuration is enabled or not.
     */
    private Boolean enabled;

    /**
     * Set the check interval.
     */
    private Duration interval;

    /**
     * Set the number of failure before reporting the service as un-healthy.
     */
    private Integer failureThreshold;

    // *************************************************
    // Properties
    // *************************************************

    /**
     * @return true if the check associated to this configuration is enabled,
     *         false otherwise.
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
    public Duration getInterval() {
        return interval;
    }

    /**
     * Set the check interval.
     */
    public void setInterval(Duration interval) {
        this.interval = interval;
    }

    /**
     * Set the check interval in a human readable format.
     */
    public void setInterval(String interval) {
        if (ObjectHelper.isNotEmpty(interval)) {
            this.interval = Duration.ofMillis(TimePatternConverter.toMilliSeconds(interval));
        } else {
            this.interval = null;
        }
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

    // *************************************************
    //
    // *************************************************
    public static Boolean defaultValueEnabled() {
        return DEFAULT_VALUE_ENABLED;
    }

    public static Duration defaultValueInterval() {
        return DEFAULT_VALUE_INTERVAL;
    }

    public static Integer defaultValueFailureThreshold() {
        return DEFAULT_VALUE_FAILURE_THRESHOLD;
    }

    public HealthCheckConfiguration copy() {
        try {
            return (HealthCheckConfiguration)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    // *************************************************
    //
    // *************************************************

    public static final class Builder implements org.apache.camel.Builder<HealthCheckConfiguration> {
        private Boolean enabled;
        private Duration interval;
        private Integer failureThreshold;

        private Builder() {
        }

        public Builder complete(HealthCheckConfiguration template) {
            if (template != null) {
                if (this.enabled == null) {
                    this.enabled = template.enabled;
                }
                if (this.interval == null) {
                    this.interval = template.interval;
                }
                if (this.failureThreshold == null) {
                    this.failureThreshold = template.failureThreshold;
                }
            }

            return this;
        }

        public Builder enabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder interval(Duration interval) {
            this.interval = interval;
            return this;
        }

        public Builder interval(Long interval) {
            return ObjectHelper.isNotEmpty(interval)
                ? interval(Duration.ofMillis(interval))
                : this;
        }

        public Builder interval(String interval) {
            return ObjectHelper.isNotEmpty(interval)
                ? interval(TimePatternConverter.toMilliSeconds(interval))
                : this;
        }

        public Builder failureThreshold(Integer failureThreshold) {
            this.failureThreshold = failureThreshold;
            return this;
        }

        @Override
        public HealthCheckConfiguration build() {
            HealthCheckConfiguration conf = new HealthCheckConfiguration();
            conf.setEnabled(ObjectHelper.supplyIfEmpty(enabled, HealthCheckConfiguration::defaultValueEnabled));
            conf.setInterval(ObjectHelper.supplyIfEmpty(interval, HealthCheckConfiguration::defaultValueInterval));
            conf.setFailureThreshold(ObjectHelper.supplyIfEmpty(failureThreshold, HealthCheckConfiguration::defaultValueFailureThreshold));

            return conf;
        }
    }
}
