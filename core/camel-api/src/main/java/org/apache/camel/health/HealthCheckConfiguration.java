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
package org.apache.camel.health;

import java.time.Duration;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.TimeUtils;

/**
 * Configuration for {@link HealthCheck}.
 */
public class HealthCheckConfiguration implements Cloneable {

    private String parent;
    private boolean enabled = true;
    private long interval;
    private int failureThreshold;

    // *************************************************
    // Properties
    // *************************************************

    public String getParent() {
        return parent;
    }

    /**
     * The id of the health check such as routes or registry (can use * as wildcard)
     */
    public void setParent(String parent) {
        this.parent = parent;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Set if the check associated to this configuration is enabled or not.
     *
     * Is default enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getInterval() {
        return interval;
    }

    /**
     * Set the check interval in milli seconds.
     */
    public void setInterval(long interval) {
        this.interval = interval;
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    /**
     * Set the number of failure before reporting the service as un-healthy.
     */
    public void setFailureThreshold(int failureThreshold) {
        this.failureThreshold = failureThreshold;
    }

    public HealthCheckConfiguration copy() {
        try {
            return (HealthCheckConfiguration) super.clone();
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
        private String parent;
        private Boolean enabled;
        private Long interval;
        private Integer failureThreshold;

        private Builder() {
        }

        public Builder complete(HealthCheckConfiguration template) {
            if (template != null) {
                if (this.parent == null) {
                    this.parent = template.parent;
                }
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

        public Builder parent(String parent) {
            this.parent = parent;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder interval(Duration interval) {
            this.interval = interval.toMillis();
            return this;
        }

        public Builder interval(String interval) {
            return ObjectHelper.isNotEmpty(interval)
                    ? interval(TimeUtils.toMilliSeconds(interval))
                    : this;
        }

        public Builder interval(long interval) {
            this.interval = interval;
            return this;
        }

        public Builder failureThreshold(int failureThreshold) {
            this.failureThreshold = failureThreshold;
            return this;
        }

        @Override
        public HealthCheckConfiguration build() {
            HealthCheckConfiguration conf = new HealthCheckConfiguration();
            if (parent != null) {
                conf.setParent(parent);
            }
            if (enabled != null) {
                conf.setEnabled(enabled);
            }
            if (interval != null) {
                conf.setInterval(interval);
            }
            if (failureThreshold != null) {
                conf.setFailureThreshold(failureThreshold);
            }
            return conf;
        }
    }
}
