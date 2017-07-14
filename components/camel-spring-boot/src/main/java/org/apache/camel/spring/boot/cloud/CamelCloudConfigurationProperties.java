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

import org.apache.camel.Expression;
import org.apache.camel.cloud.ServiceLoadBalancer;
import org.apache.camel.model.cloud.ServiceCallDefinitionConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "camel.cloud")
public class CamelCloudConfigurationProperties {
    private boolean enabled = true;
    private ServiceCall serviceCall = new ServiceCall();
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

    public ServiceCall getServiceCall() {
        return serviceCall;
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
    // Service Call
    // *****************************************

    public class ServiceCall {
        /**
         * The uri of the endpoint to send to.
         * The uri can be dynamic computed using the {@link org.apache.camel.language.simple.SimpleLanguage} expression.
         */
        private String uri;

        /**
         * The component to use.
         */
        private String component = ServiceCallDefinitionConstants.DEFAULT_COMPONENT;

        /**
         * A reference to the {@link org.apache.camel.cloud.ServiceDiscovery} to use.
         */
        private String serviceDiscovery;

        /**
         * A reference to the {@link org.apache.camel.cloud.ServiceFilter} to use.
         */
        private String serviceFilter;

        /**
         * A reference to the {@link org.apache.camel.cloud.ServiceChooser} to use.
         */
        private String serviceChooser;

        /**
         * A reference to the {@link ServiceLoadBalancer} to use.
         */
        private String loadBalancer;

        /**
         * The {@link Expression} to use.
         */
        private String expression;

        /**
         * The {@link Expression} language to use, default is ref
         */
        private String expressionLanguage = "ref";

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getComponent() {
            return component;
        }

        public void setComponent(String component) {
            this.component = component;
        }

        public String getServiceDiscovery() {
            return serviceDiscovery;
        }

        public void setServiceDiscovery(String serviceDiscovery) {
            this.serviceDiscovery = serviceDiscovery;
        }

        public String getServiceFilter() {
            return serviceFilter;
        }

        public void setServiceFilter(String serviceFilter) {
            this.serviceFilter = serviceFilter;
        }

        public String getServiceChooser() {
            return serviceChooser;
        }

        public void setServiceChooser(String serviceChooser) {
            this.serviceChooser = serviceChooser;
        }

        public String getLoadBalancer() {
            return loadBalancer;
        }

        public void setLoadBalancer(String loadBalancer) {
            this.loadBalancer = loadBalancer;
        }

        public String getExpression() {
            return expression;
        }

        public void setExpression(String expression) {
            this.expression = expression;
        }

        public String getExpressionLanguage() {
            return expressionLanguage;
        }

        public void setExpressionLanguage(String expressionLanguage) {
            this.expressionLanguage = expressionLanguage;
        }
    }

    // *****************************************
    // Load Balancer
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

    // *****************************************
    // Service Discovery
    // *****************************************

    public static class ServiceDiscoveryConfiguration {
        private Map<String, List<String>> services = new HashMap<>();
        private String cacheTimeout;

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

    public static class ServiceDiscovery extends ServiceDiscoveryConfiguration {
        private boolean enabled = true;
        private Map<String, ServiceDiscoveryConfiguration> configurations = new HashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Map<String, ServiceDiscoveryConfiguration> getConfigurations() {
            return configurations;
        }
    }

    // *****************************************
    // Service Filter
    // *****************************************

    public static class ServiceFilterConfiguration {
        private Map<String, List<String>> blacklist = new HashMap<>();

        public Map<String, List<String>> getBlacklist() {
            return blacklist;
        }
    }

    public static class ServiceFilter extends ServiceFilterConfiguration {
        private boolean enabled = true;
        private Map<String, ServiceFilterConfiguration> configurations = new HashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Map<String, ServiceFilterConfiguration> getConfigurations() {
            return configurations;
        }
    }

    // *****************************************
    // Service Chooser
    // *****************************************

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
