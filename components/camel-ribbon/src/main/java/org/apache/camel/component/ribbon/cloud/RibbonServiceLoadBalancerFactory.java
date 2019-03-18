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
package org.apache.camel.component.ribbon.cloud;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.cloud.ServiceLoadBalancer;
import org.apache.camel.cloud.ServiceLoadBalancerFactory;
import org.apache.camel.component.ribbon.RibbonConfiguration;
import org.apache.camel.spi.annotations.CloudServiceFactory;

@CloudServiceFactory("ribbon-service-load-balancer")
public class RibbonServiceLoadBalancerFactory implements ServiceLoadBalancerFactory {
    private final RibbonConfiguration configuration;

    public RibbonServiceLoadBalancerFactory() {
        this(new RibbonConfiguration());
    }

    public RibbonServiceLoadBalancerFactory(RibbonConfiguration configuration) {
        this.configuration = configuration;
    }

    // *************************************************************************
    // Properties
    // *************************************************************************

    public String getNamespace() {
        return configuration.getNamespace();
    }

    public void setNamespace(String namespace) {
        configuration.setNamespace(namespace);
    }

    public String getUsername() {
        return configuration.getUsername();
    }

    public void setUsername(String username) {
        configuration.setUsername(username);
    }

    public String getPassword() {
        return configuration.getPassword();
    }

    public void setPassword(String password) {
        configuration.setPassword(password);
    }

    public String getClientName() {
        return configuration.getClientName();
    }

    public void setClientName(String clientName) {
        configuration.setClientName(clientName);
    }

    public Map<String, String> getProperties() {
        return configuration.getProperties();
    }

    public void setProperties(Map<String, String> clientConfig) {
        configuration.setProperties(clientConfig);
    }

    // *************************************************************************
    // Factory
    // *************************************************************************

    @Override
    public ServiceLoadBalancer newInstance(CamelContext camelContext) throws Exception {
        return new RibbonServiceLoadBalancer(configuration);
    }
}
