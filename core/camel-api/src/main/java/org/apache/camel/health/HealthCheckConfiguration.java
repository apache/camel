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

import org.apache.camel.RuntimeCamelException;

/**
 * Configuration for {@link HealthCheck}.
 */
public class HealthCheckConfiguration implements Cloneable {

    // TODO: Can we avoid this as there are no configuration: only enabled true|false

    private String parent;
    private boolean enabled = true;

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

        @Override
        public HealthCheckConfiguration build() {
            HealthCheckConfiguration conf = new HealthCheckConfiguration();
            if (parent != null) {
                conf.setParent(parent);
            }
            if (enabled != null) {
                conf.setEnabled(enabled);
            }
            return conf;
        }
    }
}
