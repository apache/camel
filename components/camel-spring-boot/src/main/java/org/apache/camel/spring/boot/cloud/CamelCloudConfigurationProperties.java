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
package org.apache.camel.spring.boot.cloud;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "camel.cloud")
public class CamelCloudConfigurationProperties {
    private boolean enabled = true;
    private LoadBalancer loadBalancer = new LoadBalancer();
    private ServiceDiscovery serviceDiscovery = new ServiceDiscovery();
    private ServiceFilter serviceFilter = new ServiceFilter();
    private ServiceChooser serviceChooser = new ServiceChooser();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    public ServiceDiscovery getServiceDiscovery() {
        return serviceDiscovery;
    }

    public ServiceFilter getServiceFilter() {
        return serviceFilter;
    }

    public ServiceChooser getServiceChooser() {
        return serviceChooser;
    }

    // *****************************************
    // Nested configurations
    // *****************************************

    public static class LoadBalancer {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class ServiceDiscovery {
        private boolean enabled = true;
        private Map<String, List<String>> services = new HashMap<>();
        private String cacheTimeout;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Map<String, List<String>> getServices() {
            return services;
        }

        public String getCacheTimeout() {
            return cacheTimeout;
        }

        public void setCacheTimeout(String cacheTimeout) {
            this.cacheTimeout = cacheTimeout;
        }
    }

    public static class ServiceFilter {
        private boolean enabled = true;
        private Map<String, List<String>> blacklist = new HashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Map<String, List<String>> getBlacklist() {
            return blacklist;
        }
    }

    public static class ServiceChooser {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
