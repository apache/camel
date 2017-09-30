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
package org.apache.camel.spring.boot.actuate.health;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.spring.boot.health.HealthConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = HealthConstants.HEALTH_CHECK_INDICATOR_PREFIX)
public class CamelHealthCheckIndicatorConfiguration {
    /**
     * Global option to enable/disable this {@link org.springframework.boot.actuate.health.HealthIndicator}, default is true.
     */
    private boolean enabled = true;

    /**
     * Health check exclusion configuration.
     */
    private Exclusion exclusion = new Exclusion();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Exclusion getExclusion() {
        return exclusion;
    }

    // *****************************************
    //
    // *****************************************

    public class Exclusion {
        /**
         * A list of health check ids to exclude, either the id or a regexp.
         */
        private List<String> ids = new ArrayList<>();

        /**
         * A list of health check groups to exclude, either the group or a regexp.
         */
        private List<String> groups = new ArrayList<>();

        public List<String> getIds() {
            return ids;
        }

        public List<String> getGroups() {
            return groups;
        }
    }
}
